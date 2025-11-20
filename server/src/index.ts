import "dotenv/config";
import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import fastifyJwt from '@fastify/jwt';
import { routes } from './routes';
import { PrismaClient } from '@prisma/client';

// Declare prisma on the Fastify interface
export interface AppServices {
  prisma: PrismaClient;
}

declare module 'fastify' {
  interface FastifyInstance extends AppServices {
    verifyJWT: (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
  }
}

const server = Fastify({ logger: true });

// Setup database
const prisma = new PrismaClient();
server.decorate('prisma', prisma);

// --- START FIX: Use JWT_SECRET from .env ---
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  server.log.error('FATAL: JWT_SECRET is not defined in .env file.');
  process.exit(1);
}

server.register(fastifyJwt, { secret: JWT_SECRET });
// --- END FIX ---

// Auth decorator to protect routes
server.decorate('verifyJWT', async (request: any, reply: any) => {
  try {
    await request.jwtVerify();
  } catch (err) {
    reply.send(err);
  }
});

// Register plugins and routes
server.register(cors, { origin: true }); // Allow all origins for dev
server.register(helmet);
server.register(routes);

const start = async () => {
  try {
    await server.listen({ port: 4000, host: '0.0.0.0' });
    server.log.info('Server listening on http://0.0.0.0:4000');
  } catch (err) {
    server.log.error(err);
    await prisma.$disconnect();
    process.exit(1);
  }
};

start();