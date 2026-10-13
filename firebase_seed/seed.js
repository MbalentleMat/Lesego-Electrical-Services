// Run only in your own Firebase project using a service account with limited access.
// npm install firebase-admin && GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json node seed.js
const admin = require('firebase-admin');
const data = require('./demo_data.json');
admin.initializeApp({credential: admin.credential.applicationDefault()});
const db = admin.firestore();
(async () => {
  for (const [collection, entries] of Object.entries(data)) {
    for (const {id, ...fields} of entries) await db.collection(collection).doc(id).set(fields);
    console.log(`${collection}: ${entries.length} demo records`);
  }
})().catch(e => {console.error(e); process.exitCode=1;});
