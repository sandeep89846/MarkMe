import { FastifyPluginAsync } from 'fastify';

const studentRoutes: FastifyPluginAsync = async (fastify, opts) => {
  const { prisma } = fastify;

  /**
   * --- GET /api/student/my-subjects ---
   * Gets all subjects for the logged-in student from their timetable.
   */
  fastify.get('/my-subjects', { preHandler: [fastify.verifyJWT] }, async (request, reply) => {
    const jwtUser = request.user as any; // { studentId, deviceId }

    const student = await prisma.student.findUnique({
      where: { id: jwtUser.studentId },
      include: {
        batch: {
          include: {
            timetable: {
              include: {
                subject: true,
              },
            },
          },
        },
      },
    });

    if (!student) {
      return reply.status(404).send({ error: 'Student not found' });
    }

    // De-duplicate subjects from the timetable
    const subjectsMap = new Map();
    student.batch.timetable.forEach((entry) => {
      subjectsMap.set(entry.subject.id, entry.subject);
    });
    
    const subjects = Array.from(subjectsMap.values());
    return { subjects };
  });

  /**
   * --- GET /api/student/my-history?subjectId=... ---
   * Gets all VERIFIED attendance records for a specific subject.
   */
  fastify.get('/my-history', { preHandler: [fastify.verifyJWT] }, async (request, reply) => {
    const jwtUser = request.user as any; // { studentId, deviceId }
    const { subjectId } = request.query as any;
    
    if (!subjectId) {
      return reply.status(400).send({ error: 'subjectId query parameter is required' });
    }

    const records = await prisma.attendanceRecord.findMany({
      where: {
        studentId: jwtUser.studentId,
        session: {
          subjectId: subjectId,
        },
        status: 'VERIFIED', // Only show verified records
      },
      orderBy: {
        tsClient: 'desc',
      },
      include: {
        session: { // Include session to get subject info if needed
          include: {
            subject: true
          }
        }
      }
    });

    // We can simplify the response
    const history = records.map(r => ({
      id: r.id,
      className: r.session.subject.name,
      status: r.status,
      timestamp: r.tsClient.toISOString(), // Use the client's timestamp for the record
    }));

    return { history };
  });
};

export default studentRoutes;