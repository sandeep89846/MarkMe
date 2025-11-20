import { FastifyPluginAsync } from 'fastify';
import timeRoutes from './routes/time';
import authRoutes from './routes/auth';
import attendanceRoutes from './routes/attendance';
import sessionRoutes from './routes/session';
import teacherRoutes from './routes/teacher';
import studentRoutes from './routes/student'; // <-- ADD THIS

// This file registers all our route modules
export const routes: FastifyPluginAsync = async (fastify, opts) => {
  
  // API Routes (for the app)
  fastify.register(timeRoutes, { prefix: '/api/time' });
  fastify.register(authRoutes, { prefix: '/api/auth' });
  fastify.register(attendanceRoutes, { prefix: '/api/attendance' });
  fastify.register(sessionRoutes, { prefix: '/api/session' });
  fastify.register(studentRoutes, { prefix: '/api/student' }); // <-- ADD THIS

  // Human-facing Route (for the browser)
  // This is registered at the ROOT, not under /api
  fastify.register(teacherRoutes, { prefix: '/' });
};