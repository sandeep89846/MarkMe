/*
  Warnings:

  - A unique constraint covering the columns `[subjectId,expiresAt]` on the table `Session` will be added. If there are existing duplicate values, this will fail.

*/
-- CreateIndex
CREATE UNIQUE INDEX "Session_subjectId_expiresAt_key" ON "Session"("subjectId", "expiresAt");
