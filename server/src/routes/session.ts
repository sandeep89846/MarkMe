import { FastifyPluginAsync } from 'fastify';
import { v4 as uuidv4 } from 'uuid';
import { DateTime } from 'luxon';

const sessionRoutes: FastifyPluginAsync = async (fastify, opts) => {
  const { prisma } = fastify;

  /**
   * --- /session/current ---
   * Finds the user's *currently scheduled* class from the Timetable.
   * If a class is active, it finds or creates a "live" Session for it.
   */
  fastify.get('/current', { preHandler: [fastify.verifyJWT] }, async (request, reply) => {
    const jwtUser = request.user as any; // { studentId, deviceId }

    // 1. Get the student's batch
    const student = await prisma.student.findUnique({
      where: { id: jwtUser.studentId },
      include: { batch: true },
    });

    if (!student) {
      return reply.status(404).send({ error: 'Student not found' });
    }

    // 2. Get current time in server's timezone (assumed India)
    const now = DateTime.now().setZone('Asia/Kolkata');
    const currentDayOfWeek = now.weekday === 7 ? 0 : now.weekday; // Convert Luxon (1-7) to (0-6)
    const currentTime = now.toFormat('HH:mm'); // "14:35"

    // 3. Find a matching class in the timetable
    const scheduleEntry = await prisma.timetable.findFirst({
      where: {
        batchId: student.batchId,
        dayOfWeek: currentDayOfWeek,
        startTime: { lte: currentTime },
        endTime: { gte: currentTime },
      },
      include: { subject: true },
    });

    if (!scheduleEntry) {
      return reply.status(404).send({ error: 'No active class found in your schedule' });
    }

    // 4. A class is scheduled! Find or create the "live" session.
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

    // 5. Return the live session info
    return {
      sessionId: session.id,
      className: scheduleEntry.subject.name,
      location: {
        latitude: session.lat,
        longitude: session.lon,
      },
      qrRotationIntervalMs: 15000,
    };
  });

  // Endpoint for the *Teacher's* UI to generate a new QR nonce
  fastify.get('/qr', async (request, reply) => {
    const { sessionId, secret } = request.query as any;

    if (secret !== process.env.TEACHER_SECRET) {
      return reply.status(401).send({ error: 'Unauthorized: Invalid secret' });
    }

    if (!sessionId) {
      return reply.status(400).send({ error: 'sessionId is required' });
    }

    const qrNonce = uuidv4();
    const ts = new Date();

    await prisma.teacherNonce.create({
      data: {
        nonce: qrNonce,
        session: { connect: { id: sessionId } },
        ts: ts,
      },
    });

    return {
      qrNonce: qrNonce,
      sessionId: sessionId,
      ts: ts.toISOString(),
    };
  });
};

export default sessionRoutes;