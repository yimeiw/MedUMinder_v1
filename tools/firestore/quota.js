// Stop at the FIRST "quota exceeded" error with a friendly message, instead of the Firestore library
// silently retrying it for up to 10 minutes (that only burns time; the daily limit does not come back).
//
// Two layers:
//  1. single reads/writes (get, getAll, commit): the retry setting below removes RESOURCE_EXHAUSTED
//     from the codes the client retries, so the error reaches our catch right away;
//  2. collection queries: the library retries these itself (not configurable), but it logs every retry,
//     so the log hook below stops the script on the first quota message.

const QUOTA = /RESOURCE_EXHAUSTED|quota exceeded|resource[_ -]exhausted/i;

const NO_QUOTA_RETRY = {
  interfaces: {
    'google.firestore.v1.Firestore': {
      // the library's own retry lists, minus RESOURCE_EXHAUSTED (writes keep exactly their other codes)
      retry_codes: {
        no_quota_read: ['DEADLINE_EXCEEDED', 'INTERNAL', 'UNAVAILABLE'],
        no_quota_write: ['UNAVAILABLE'],
        no_quota_batch_write: ['ABORTED', 'UNAVAILABLE'],
      },
      methods: {
        ...Object.fromEntries([
          'GetDocument', 'ListDocuments', 'DeleteDocument', 'BatchGetDocuments', 'BeginTransaction', 'Rollback',
          'RunQuery', 'RunAggregationQuery', 'PartitionQuery', 'Listen', 'ListCollectionIds',
        ].map((m) => [m, { retry_codes_name: 'no_quota_read' }])),
        UpdateDocument: { retry_codes_name: 'no_quota_write' },
        CreateDocument: { retry_codes_name: 'no_quota_write' },
        Commit: { retry_codes_name: 'no_quota_write' },
        BatchWrite: { retry_codes_name: 'no_quota_batch_write' },
      },
    },
  },
};

function isQuotaError(e) {
  if (!e) return false;
  return e.code === 8 || e.code === 'resource-exhausted' || QUOTA.test(String(e.message || e));
}

function quotaStop() {
  console.error('\n========================================================================');
  console.error('STOPPED: Firestore daily limit reached (free Spark plan quota exceeded).');
  console.error('Nothing more will be read or changed by this run.');
  console.error('Try again after the daily reset: midnight US Pacific time');
  console.error('(about 15:00 WITA while US summer time lasts, 16:00 WITA after early November).');
  console.error('If this was an --apply run, part of it may already be done. That is safe:');
  console.error('run the same command again after the reset and it continues where it stopped.');
  console.error('========================================================================');
  process.exit(2);
}

/** Call right after admin.firestore(), before the first read. */
function guardQuota(admin, db) {
  if (db && typeof db.settings === 'function') db.settings({ clientConfig: NO_QUOTA_RETRY });
  if (admin && admin.firestore && typeof admin.firestore.setLogFunction === 'function') {
    admin.firestore.setLogFunction((msg) => { if (QUOTA.test(String(msg))) quotaStop(); });
  }
  return db;
}

/** Use as: main().catch(fatal) */
function fatal(e) {
  if (isQuotaError(e)) quotaStop();
  console.error(e);
  process.exit(1);
}

module.exports = { guardQuota, fatal, isQuotaError, NO_QUOTA_RETRY };
