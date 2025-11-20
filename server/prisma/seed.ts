import "dotenv/config";
import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

async function main() {
  console.log('Start seeding ...');

  // --- START FIX: Use ADMIN_EMAIL from .env ---
  const adminEmail = process.env.ADMIN_EMAIL;
  if (!adminEmail) {
    throw new Error('ADMIN_EMAIL is not defined in your .env file.');
  }
  // --- END FIX ---

  // 1. Create Batches
  const batchIMT = await prisma.batch.upsert({
    where: { name: '2022IMT' },
    update: {},
    create: { name: '2022IMT' },
  });
  const batchBCS = await prisma.batch.upsert({
    where: { name: '2022BCS' },
    update: {},
    create: { name: '2022BCS' },
  });
  console.log('Created batches: 2022IMT, 2022BCS');

  // 2. Create Students and assign them to a Batch
  await prisma.student.upsert({
    where: { email: adminEmail },
    update: {},
    create: {
      rollNo: 'IMT-001',
      name: 'Admin Student',
      email: adminEmail,
      batchId: batchIMT.id,
    },
  });

  await prisma.student.upsert({
    where: { email: 'imt_2022010@iiitm.ac.in' },
    update: {},
    create: {
      rollNo: 'IMT-010',
      name: 'Ajay Kumar Meena',
      email: 'imt_2022010@iiitm.ac.in',
      batchId: batchIMT.id,
    },
  });

  await prisma.student.upsert({
    where: { email: 'imt_2022011@iiitm.ac.in' },
    update: {},
    create: {
      rollNo: 'IMT-011',
      name: 'Alapati Saai Saahitthi',
      email: 'imt_2022011@iiitm.ac.in',
      batchId: batchIMT.id,
    },
  });
  console.log('Created students.');

  // 3. Create Subjects
  
  // --- SUBJECTS FOR 2022IMT (FROM IMAGE) ---
  const subjectMOM = await prisma.subject.upsert({
    where: { code: 'IMT-301' },
    update: {},
    create: { code: 'IMT-301', name: 'MOM' },
  });

  const subjectDM = await prisma.subject.upsert({
    where: { code: 'IMT-302' },
    update: {},
    create: { code: 'IMT-302', name: 'DM' },
  });

  const subjectCV = await prisma.subject.upsert({
    where: { code: 'IMT-303' },
    update: {},
    create: { code: 'IMT-303', name: 'CV' },
  });

  const subjectML = await prisma.subject.upsert({
    where: { code: 'IMT-304' },
    update: {},
    create: { code: 'IMT-304', name: 'ML' },
  });

  const subjectPR = await prisma.subject.upsert({
    where: { code: 'IMT-305' },
    update: {},
    create: { code: 'IMT-305', name: 'PR' },
  });

  const subjectDA = await prisma.subject.upsert({
    where: { code: 'IMT-306' },
    update: {},
    create: { code: 'IMT-306', name: 'DA' },
  });

  const subjectMCOM = await prisma.subject.upsert({
    where: { code: 'IMT-307' },
    update: {},
    create: { code: 'IMT-307', name: 'MCOM' },
  });

  const subjectML_Lab = await prisma.subject.upsert({
    where: { code: 'IMT-308L' },
    update: {},
    create: { code: 'IMT-308L', name: 'ML Lab' },
  });

  const subjectDM_Lab = await prisma.subject.upsert({
    where: { code: 'IMT-309L' },
    update: {},
    create: { code: 'IMT-309L', name: 'DM Lab' },
  });

  const subjectMCOM_Lab = await prisma.subject.upsert({
    where: { code: 'IMT-310L' },
    update: {},
    create: { code: 'IMT-310L', name: 'MCOM Lab' },
  });

  // --- SUBJECT FOR 2022BCS (LEGACY) ---
  const subjectNetworks = await prisma.subject.upsert({
    where: { code: 'CS-301' },
    update: {},
    create: {
      code: 'CS-301',
      name: 'Computer Networks',
    },
  });

  console.log('Created all subjects.');

  // 4. Create the Timetable (The Schedule)
  const classLocation = {
    lat: 26.2502228,
    lon: 78.1698032,
  };

  // --- NEW TIMETABLE START ---
  // Delete all old timetable entries for IMT batch to avoid conflicts
  await prisma.timetable.deleteMany({
    where: { batchId: batchIMT.id },
  });

  // Define all periods for 2022IMT
  const timetableData = [
    // Monday (dayOfWeek: 1)
    { batchId: batchIMT.id, subjectId: subjectMOM.id, dayOfWeek: 1, startTime: '09:00', endTime: '10:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDM.id, dayOfWeek: 1, startTime: '10:00', endTime: '11:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectCV.id, dayOfWeek: 1, startTime: '11:00', endTime: '12:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectPR.id, dayOfWeek: 1, startTime: '14:00', endTime: '15:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDA.id, dayOfWeek: 1, startTime: '15:00', endTime: '16:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectMCOM.id, dayOfWeek: 1, startTime: '16:00', endTime: '17:00', ...classLocation },

    // Tuesday (dayOfWeek: 2)
    { batchId: batchIMT.id, subjectId: subjectML.id, dayOfWeek: 2, startTime: '12:00', endTime: '13:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectML_Lab.id, dayOfWeek: 2, startTime: '14:00', endTime: '16:00', ...classLocation }, // 2-hr lab

    // Wednesday (dayOfWeek: 3)
    { batchId: batchIMT.id, subjectId: subjectMOM.id, dayOfWeek: 3, startTime: '09:00', endTime: '10:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDM.id, dayOfWeek: 3, startTime: '10:00', endTime: '11:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectCV.id, dayOfWeek: 3, startTime: '11:00', endTime: '12:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectML.id, dayOfWeek: 3, startTime: '12:00', endTime: '13:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectPR.id, dayOfWeek: 3, startTime: '14:00', endTime: '15:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDA.id, dayOfWeek: 3, startTime: '15:00', endTime: '16:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectMCOM.id, dayOfWeek: 3, startTime: '16:00', endTime: '17:00', ...classLocation },

    // Thursday (dayOfWeek: 4)
    { batchId: batchIMT.id, subjectId: subjectDM_Lab.id, dayOfWeek: 4, startTime: '11:00', endTime: '13:00', ...classLocation }, // 2-hr lab
    { batchId: batchIMT.id, subjectId: subjectMCOM_Lab.id, dayOfWeek: 4, startTime: '16:00', endTime: '18:00', ...classLocation }, // 2-hr lab
    { 
      batchId: batchIMT.id, 
      subjectId: subjectMOM.id, // MOM Class
      dayOfWeek: 4,             // 4 = Thursday
      startTime: '18:00',       // 6:00 PM
      endTime: '23:00',         // 11:00 PM (Covers right now)
      ...classLocation 
    },


    // Friday (dayOfWeek: 5)
    { batchId: batchIMT.id, subjectId: subjectMOM.id, dayOfWeek: 5, startTime: '09:00', endTime: '10:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDM.id, dayOfWeek: 5, startTime: '10:00', endTime: '11:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectCV.id, dayOfWeek: 5, startTime: '11:00', endTime: '12:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectML.id, dayOfWeek: 5, startTime: '12:00', endTime: '13:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectPR.id, dayOfWeek: 5, startTime: '14:00', endTime: '15:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectDA.id, dayOfWeek: 5, startTime: '15:00', endTime: '16:00', ...classLocation },
    { batchId: batchIMT.id, subjectId: subjectMCOM.id, dayOfWeek: 5, startTime: '16:00', endTime: '17:00', ...classLocation },
  ];

  // Create all the timetable entries for IMT
  await prisma.timetable.createMany({
    data: timetableData,
  });
  console.log(`Created ${timetableData.length} timetable entries for 2022IMT.`);

  // Legacy entry for BCS batch (for testing)
  await prisma.timetable.upsert({
    where: { id: 'tt-bcs-1' },
    update: {},
    create: {
      id: 'tt-bcs-1',
      batchId: batchBCS.id,
      subjectId: subjectNetworks.id,
      dayOfWeek: 1, // Monday
      startTime: '09:00',
      endTime: '10:00',
      ...classLocation,
    },
  });
  console.log('Created test entry for 2022BCS.');
  // --- NEW TIMETABLE END ---

  console.log('Seeding finished.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });