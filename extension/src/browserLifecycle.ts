import type { Job } from "./types.js";

const RECENT_JOB_WINDOW_MS = 30 * 60 * 1000;

export function hasRecentOpenJob(jobs: Job[], now = Date.now()): boolean {
  return jobs.some(job => {
    // FAILED and NEEDS_USER are terminal in this installation: the user does
    // not operate the companion PC. They must never keep Chrome open.
    if (!["QUEUED", "CLAIMED", "SEARCHING", "NAVIGATING", "CAPTURING"].includes(job.state)) {
      return false;
    }
    const createdAt = Date.parse(job.createdAt);
    return Number.isFinite(createdAt) && now - createdAt <= RECENT_JOB_WINDOW_MS;
  });
}
