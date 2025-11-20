import { FastifyPluginAsync } from 'fastify';
// You will need to install the Google Auth Library:
// npm install google-auth-library
import { OAuth2Client } from 'google-auth-library';

// TODO: Get this from your Google Cloud Console
const CLIENT_ID = "785046225223-9pvc27uun2ol3rrpa7sclj8jenbrg2l6.apps.googleusercontent.com";
const client = new OAuth2Client(CLIENT_ID);

const authRoutes: FastifyPluginAsync = async (fastify, opts) => {
  const { prisma } = fastify;

  /**
   * --- /auth/google-signin ---
   * Replaces /register-or-login.
   * 1. Verifies Google ID Token.
   * 2. Finds student by email.
   * 3. Links their googleSub (one-time).
   * 4. Registers their device.
   * 5. Returns session JWT.
   */
  fastify.post('/google-signin', async (request, reply) => {
    const body = request.body as any;
    if (!body.idToken || !body.deviceId || !body.pubkeyPem) {
      return reply.status(400).send({ error: 'idToken, deviceId, and pubkeyPem are required' });
    }

    const { idToken, deviceId, pubkeyPem } = body;

    let payload;
    try {
      // 1. Verify Google ID Token
      const ticket = await client.verifyIdToken({
        idToken: idToken,
        audience: CLIENT_ID,
      });
      payload = ticket.getPayload();
      
      if (!payload || !payload.email || !payload.sub) {
        return reply.status(401).send({ error: 'Invalid Google token' });
      }
    } catch (e: any) {
      return reply.status(401).send({ error: 'Google token verification failed', details: e.message });
    }

    const { email, sub: googleSub, name } = payload;

    try {
      // 2. Find student by email (which admin pre-loaded)
      const student = await prisma.student.findUnique({ // <-- FIX: camelCase
        where: { email: email },
      });

      if (!student) {
        return reply.status(403).send({ error: 'Account not found in university roster. Contact admin.' });
      }

      // 3. Update student record with their googleSub and name (one-time)
      const updatedStudent = await prisma.student.update({ // <-- FIX: camelCase
        where: { id: student.id },
        data: { 
          googleSub: googleSub, // Links Google account
          name: student.name || name, // Updates their name
        },
      });

      // 4. Register or update the device
      const device = await prisma.device.upsert({
        where: { deviceId: deviceId },
        // --- FIX: Use relational 'connect' syntax ---
        update: {
          pubkeyPem: pubkeyPem,
          active: true,
          student: { connect: { id: updatedStudent.id } },
        },
        create: {
          deviceId: deviceId,
          pubkeyPem: pubkeyPem,
          active: true,
          student: { connect: { id: updatedStudent.id } },
        },
        // --- END FIX ---
      });

      // 5. Issue our *own* session token
      const token = fastify.jwt.sign(
        { studentId: student.id, deviceId: device.deviceId }, // Use studentId now
        { expiresIn: '30d' }
      );
      
      return { token, status: 'login_success' };

    } catch (e: any) {
      fastify.log.error(e, 'Failed during sign-in or device registration');
      return reply.status(500).send({ error: 'Failed to register device.' });
    }
  });
};

export default authRoutes;