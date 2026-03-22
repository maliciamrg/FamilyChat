"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sendMessageNotification = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();
exports.sendMessageNotification = functions.firestore
    .document('family_chat/{messageId}')
    .onCreate(async (snap, context) => {
    const message = snap.data();
    const familyId = message.familyId;
    const senderEmail = message.sender;
    // Get tokens of family members except the sender
    const tokensSnapshot = await admin.firestore()
        .collection('device_tokens')
        .where('familyId', '==', familyId)
        .get();
    const tokens = [];
    tokensSnapshot.forEach(doc => {
        const token = doc.data().token;
        if (token) {
            tokens.push(token);
        }
    });
    if (tokens.length > 0) {
        const payload = {
            notification: {
                title: 'New Message in Family Chat',
                body: `${senderEmail}: ${message.text}`,
            },
            data: {
                familyId: familyId,
                messageId: context.params.messageId,
            },
        };
        // Send to all tokens
        const response = await admin.messaging().sendToDevice(tokens, payload);
        console.log('Notifications sent:', response);
    }
});
//# sourceMappingURL=index.js.map