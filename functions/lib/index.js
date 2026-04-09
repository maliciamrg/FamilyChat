"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.sendMessageNotification = void 0;
const firestore_1 = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();
exports.sendMessageNotification = (0, firestore_1.onDocumentCreated)('family_chat/{messageId}', async (event) => {
    const snap = event.data;
    if (!snap)
        return;
    const message = snap.data();
    const familyId = message.familyId;
    const senderUid = message.senderUid;
    const senderName = message.senderName || message.sender;
    const messageId = event.params.messageId;
    // Get tokens of family members except the sender
    const tokensSnapshot = await admin.firestore()
        .collection('device_tokens')
        .where('familyId', '==', familyId)
        .get();
    const tokens = [];
    tokensSnapshot.forEach(doc => {
        // Skip the sender's own token
        if (doc.id === senderUid)
            return;
        const token = doc.data().token;
        if (token) {
            tokens.push(token);
        }
    });
    if (tokens.length === 0)
        return;
    const multicastMessage = {
        tokens,
        notification: {
            title: 'New Message in Family Chat',
            body: `${senderName}: ${message.text}`,
        },
        data: {
            familyId: familyId,
            messageId: messageId,
        },
    };
    const response = await admin.messaging().sendEachForMulticast(multicastMessage);
    console.log(`Notifications sent: ${response.successCount} success, ${response.failureCount} failure`);
    // Clean up stale tokens
    const failedTokens = [];
    response.responses.forEach((resp, idx) => {
        if (!resp.success && resp.error) {
            const code = resp.error.code;
            if (code === 'messaging/invalid-registration-token' ||
                code === 'messaging/registration-token-not-registered') {
                failedTokens.push(tokens[idx]);
            }
        }
    });
    if (failedTokens.length > 0) {
        const batch = admin.firestore().batch();
        const staleSnap = await admin.firestore()
            .collection('device_tokens')
            .where('token', 'in', failedTokens)
            .get();
        staleSnap.forEach(doc => batch.delete(doc.ref));
        await batch.commit();
        console.log(`Cleaned up ${failedTokens.length} stale tokens`);
    }
});
//# sourceMappingURL=index.js.map