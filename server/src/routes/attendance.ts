import { FastifyPluginAsync } from 'fastify';
import crypto from 'crypto';
import haversine from 'haversine';

/**
 * Deterministic JSON canonicalizer.
 * This logic MUST be byte-for-byte identical to the client's implementation.
 * Rules: Sort object keys alphabetically, serialize compactly.
 */
function canonicalize(obj: any): string {
  if (obj === null || typeof obj !== 'object') {
    return JSON.stringify(obj);
  }
  if (Array.isArray(obj)) {
    return '[' + obj.map(canonicalize).join(',') + ']';
  }
  const keys = Object.keys(obj).sort();
  return '{' + keys.map(k => JSON.stringify(k) + ':' + canonicalize(obj[k])).join(',') + '}';
}

/**
 * Helper to verify ECDSA P-256 signatures from the Android Keystore.
 * Expects a PEM-formatted public key and a Base64 signature.
 */
function verifyEcdsaPemSignature(pubkeyPem: string, dataBuf: Buffer, sigB64: string): boolean {
  try {
    const verify = crypto.createVerify('SHA256');
    verify.update(dataBuf);
    verify.end();
    const sigBuffer = Buffer.from(sigB64, 'base64');
    return verify.verify(pubkeyPem, sigBuffer);
  } catch (e) {
    return false;
  }
}

const attendanceRoutes: FastifyPluginAsync = async (fastify, opts) => {
  const { prisma } = fastify;

  /**
   * This is the main endpoint for receiving attendance.
   * It is protected and requires a valid session JWT.
   */
  fastify.post('/batch', { preHandler: [fastify.verifyJWT] }, async (request: any, reply) => {
    const body = request.body as any;
    const jwtUser = request.user as any; // { studentId, deviceId }

    if (!body || !Array.isArray(body.events)) {
      return reply.status(400).send({ error: 'invalid body' });
    }

    const results = [];

    // CONFIGURATION
    const MAX_DISTANCE_METERS = 50; 
    const MAX_NONCE_AGE_MS = 300000; // 5 minutes

    for (const ev of body.events) {
      const attendance = ev.attendance;
      const studentSigB64 = ev.student_sig;
      const idempotencyKey = attendance?.idempotency_key;

      if (!attendance || !studentSigB64 || !idempotencyKey) {
        results.push({ id: idempotencyKey || null, status: 'invalid_payload' });
        continue;
      }
      
      try {
        // 1. Check if already processed (Idempotency)
        const prev = await prisma.attendanceRecord.findUnique({
          where: { id: idempotencyKey },
        });
        if (prev) {
          results.push({ id: idempotencyKey, status: prev.status });
          continue;
        }

        // 2. Device & Auth Check
        if (attendance.device_id !== jwtUser.deviceId) {
          results.push({ id: idempotencyKey, status: 'device_mismatch' });
          continue;
        }
        const device = await prisma.device.findUnique({
          where: { deviceId: jwtUser.deviceId },
        });

        if (!device || !device.active || device.studentId !== jwtUser.studentId) {
          results.push({ id: idempotencyKey, status: 'unauthorized_device' });
          continue;
        }
        
        // 3. Signature Verification (SECURE)
        const canonical = canonicalize(attendance);
        const canonicalBuffer = Buffer.from(canonical, 'utf8');
        const isSigValid = verifyEcdsaPemSignature(device.pubkeyPem, canonicalBuffer, studentSigB64);
        if (!isSigValid) {
          results.push({ id: idempotencyKey, status: 'bad_signature' });
          continue;
        }

        // 3a. Uniqueness Check (Prevent duplicate verified records)
        const existingVerifiedRecord = await prisma.attendanceRecord.findFirst({
          where: {
            studentId: jwtUser.studentId,
            sessionId: attendance.sess,
            status: 'VERIFIED',
          },
        });

        if (existingVerifiedRecord) {
          results.push({ id: idempotencyKey, status: 'ok' });
          continue; 
        }

        // 4. Session Verification
        const session = await prisma.session.findUnique({
          where: { id: attendance.sess } 
        });
        if (!session) {
          results.push({ id: idempotencyKey, status: 'unknown_session' });
          continue;
        }
        
        // 5. QR Nonce Verification
        const nonceRow = await prisma.teacherNonce.findUnique({
          where: { nonce: attendance.qrNonce }
        });
        if (!nonceRow || nonceRow.sessionId !== session.id) {
          results.push({ id: idempotencyKey, status: 'nonce_missing' });
          continue;
        }
        
        // 6. Time-window check
        const tsClient = new Date(attendance.ts_client).getTime();
        const nonceTs = new Date(nonceRow.ts).getTime();
        const timeDiff = Math.abs(tsClient - nonceTs);
        if (timeDiff > MAX_NONCE_AGE_MS) { 
          results.push({ id: idempotencyKey, status: 'nonce_time_mismatch' });
          continue;
        }

        // 7. Location Verification
        const classLocation = { latitude: session.lat, longitude: session.lon };
        const studentLocation = { latitude: attendance.lat, longitude: attendance.lon };
        const distance = haversine(classLocation, studentLocation, { unit: 'meter' });
        if (distance > MAX_DISTANCE_METERS) {
          results.push({ 
            id: idempotencyKey, 
            status: 'location_mismatch',
            metadata: `Distance was ${Math.round(distance)}m.`
          });
          continue;
        }

        // 8. Persist
        await prisma.attendanceRecord.create({
          data: {
            id: idempotencyKey,
            sessionId: session.id,
            studentId: jwtUser.studentId,
            deviceId: jwtUser.deviceId,
            qrNonce: attendance.qrNonce,
            lat: attendance.lat,
            lon: attendance.lon,
            tsClient: new Date(attendance.ts_client),
            studentSig: studentSigB64,
            attendanceBlob: canonical,
            status: 'VERIFIED',
          },
        });
        results.push({ id: idempotencyKey, status: 'ok' });

      } catch (err: any) {
        fastify.log.error(err);
        results.push({ id: idempotencyKey, status: 'server_error' });
      }
    }

    return { results, server_time: new Date().toISOString() };
  });
};

export default attendanceRoutes;