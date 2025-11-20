import { FastifyPluginAsync } from 'fastify';

// Provides the trusted server time [cite: 2155-2162, 2531-2542]
const timeRoutes: FastifyPluginAsync = async (fastify, opts) => {
  fastify.get('/', async (request, reply) => {
    const now = new Date().toISOString();
    return { utc: now };
  });
};

export default timeRoutes;