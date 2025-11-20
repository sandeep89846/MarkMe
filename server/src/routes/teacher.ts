import { FastifyPluginAsync } from 'fastify';
import QRCode from 'qrcode';
import { v4 as uuidv4 } from 'uuid';
import { DateTime } from 'luxon';

const teacherRoutes: FastifyPluginAsync = async (fastify, opts) => {
  const { prisma } = fastify;

  // This route serves the teacher's HTML page
  fastify.get('/teacher', async (request, reply) => {
    const query = request.query as any;
    const { subjectId, secret } = query;

    if (secret !== process.env.TEACHER_SECRET) {
      reply.status(401).type('text/html');
      return `
        <html><head><title>Error</title><body style="font-family: sans-serif; text-align: center; padding-top: 50px;">
          <h1>401 Unauthorized</h1>
          <p>Invalid or missing secret.</p>
        </body></html>
      `;
    }

    let qrImageDataUrl: string | null = null;
    let statusMessage = "Please select a subject to start the session.";
    let pageTitle = "Teacher Portal";

    // STEP 2: A subjectId was provided, so generate the QR code
    if (subjectId) {
      try {
        // 1. Find the active timetable entry for this subject
        const now = DateTime.now().setZone('Asia/Kolkata');
        const currentDayOfWeek = now.weekday === 7 ? 0 : now.weekday; 
        const currentTime = now.toFormat('HH:mm'); 

        const scheduleEntry = await prisma.timetable.findFirst({
          where: {
            subjectId: subjectId,
            dayOfWeek: currentDayOfWeek,
            startTime: { lte: currentTime },
            endTime: { gte: currentTime },
          },
          include: { subject: true },
        });

        if (!scheduleEntry) {
          statusMessage = "Error: This subject is not scheduled for this time.";
        } else {
          pageTitle = scheduleEntry.subject.name;

          // 2. Find or create the "live" session
          const nowUtc = new Date();
          const classEndTime = DateTime.fromISO(now.toISODate() + 'T' + scheduleEntry.endTime, { zone: 'Asia/Kolkata' }).toJSDate();

          let session;
          try {
            // Atomic Find-or-Create (Part 1)
            const existingSession = await prisma.session.findFirst({
              where: {
                subjectId: scheduleEntry.subjectId,
                expiresAt: { gte: nowUtc },
              },
            });

            if (existingSession) {
              session = existingSession;
            } else {
              session = await prisma.session.create({
                data: {
                  subjectId: scheduleEntry.subjectId,
                  lat: scheduleEntry.lat,
                  lon: scheduleEntry.lon,
                  expiresAt: classEndTime,
                },
              });
            }
          } catch (e: any) {
            // Atomic Find-or-Create (Part 2)
            fastify.log.warn('Race condition detected. Re-fetching session.');
            session = await prisma.session.findFirst({
              where: {
                subjectId: scheduleEntry.subjectId,
                expiresAt: { gte: nowUtc },
              },
            });
            if (!session) throw new Error("Failed to resolve session race condition.");
          }
          
          fastify.log.info(`Teacher is generating QR for session: ${session.id}`);

          // 3. Generate the QR nonce
          const qrNonce = uuidv4();
          const ts = new Date();

          await prisma.teacherNonce.create({
            data: {
              nonce: qrNonce,
              session: { connect: { id: session.id } }, 
              ts: ts,
            },
          });

          // 4. Stringify
          const qrJsonString = JSON.stringify({
            qrNonce: qrNonce,
            sessionId: session.id,
            ts: ts.toISOString(),
          });

          // 5. Generate Image
          qrImageDataUrl = await QRCode.toDataURL(qrJsonString);
          statusMessage = `QR code generated for ${scheduleEntry.subject.name}. Students can now scan.`;
        }
      } catch (e: any) {
        fastify.log.error(e, "Failed to generate QR code");
        statusMessage = `Error: Could not generate QR code. ${e.message}`;
      }
    }
    
    // STEP 1: Get all subjects
    const allSubjects = await prisma.subject.findMany();

    reply.type('text/html');
    return `
      <!DOCTYPE html>
      <html>
      <head>
          <title>MarkMe Teacher Portal</title>
          <style>
              body { font-family: sans-serif; display: grid; place-items: center; min-height: 90vh; background: #f4f4f4; margin: 0; }
              div.container { text-align: center; background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); max-width: 500px; }
              img { margin-top: 1rem; border: 1px solid #ccc; }
              p { color: #666; }
              #status { font-weight: bold; margin-top: 1.5rem; }
              ul { list-style: none; padding: 0; margin: 1rem 0; }
              li { margin: 0.5rem 0; }
              a.subject-button {
                display: block; padding: 0.75rem; 
                background: #007aff; color: white; border: none; 
                border-radius: 4px; font-size: 1rem; cursor: pointer;
                text-decoration: none;
              }
              a.subject-button:hover { background: #0056b3; }
          </style>
      </head>
      <body>
          <div class="container">
              <h1>${pageTitle}</h1>
              
              ${
                qrImageDataUrl
                  ?
                  `
                    <p>Scan this code to mark attendance:</p>
                    <img src="${qrImageDataUrl}" alt="QR Code" width="300" height="300">
                    <a href="/teacher?secret=${secret}" style="margin-top: 1rem; display: inline-block;">Back to subject list</a>
                    `
                  : `
                    <p>Select your subject to start a session:</p>
                    <ul>
                      ${allSubjects.map(subject => `
                        <li>
                          <a class="subject-button" href="/teacher?subjectId=${subject.id}&secret=${secret}">
                            ${subject.name} (${subject.code})
                          </a>
                          </li>
                      `).join('')}
                    </ul>
                  `
              }
          
              <p id="status">${statusMessage}</p>

          </div>
      </body>
      </html>
    `;
  });
};

export default teacherRoutes;