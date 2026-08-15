# Firebase Firestore Security Rules

This document contains the secure Firebase Firestore rules specifically designed and configured for the **DASMO PHOTO PRINT** application, ensuring complete security and restricting administrative capabilities strictly to the authorized administrator account.

## Authorized Administrator Account
* **Admin Email:** `subhojitpaul26042004@gmail.com`

---

## 🔒 Recommended Firestore Security Rules

Copy and paste the rules below directly into your **Firebase Console** -> **Firestore Database** -> **Rules** tab:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Helper functions to keep rules clean and maintainable
    function isAuthenticated() {
      return request.auth != null;
    }

    function isUserEmail(email) {
      return isAuthenticated() && request.auth.token.email.toLowerCase() == email.toLowerCase();
    }

    function isAdmin() {
      return isAuthenticated() && request.auth.token.email.toLowerCase() == 'subhojitpaul26042004@gmail.com';
    }

    // ==========================================
    // 🛡️ RULES FOR DASMO PHOTO PRINT (users collection)
    // ==========================================
    match /users/{email} {
      // 1. Admin has complete, unrestricted access to all user documents
      allow read, write: if isAdmin();

      // 2. Standard users can read (get) only their own document (no listing allowed)
      allow get: if isUserEmail(email);

      // 3. New registration: standard users can create their own document,
      //    but they MUST be initialized as 'user' role and 'pending' status
      allow create: if isUserEmail(email) 
        && request.resource.data.role == 'user'
        && request.resource.data.status == 'pending';

      // 4. Login and session updates: standard users can update their own device/token info,
      //    but they are STRICTLY FORBIDDEN from modifying their status, role, or expiry timestamp
      allow update: if isUserEmail(email)
        && request.resource.data.role == resource.data.role
        && request.resource.data.status == resource.data.status
        && (!request.resource.data.diff(resource.data).affectedKeys().hasAny(['role', 'status', 'expiryTimestamp']));

      // 5. Deletion: Only the administrator can permanently delete user accounts
      allow delete: if isAdmin();
    }

    // ==========================================
    // 📱 RULES FOR YOUR OTHER APP (DASMO SCANNER)
    // ==========================================
    // When you implement your scanner app, place its rules below.
    // For example, if it uses a "scans" collection:
    //
    // match /scans/{scanId} {
    //   allow read, write: if isAuthenticated() && (resource.data.userId == request.auth.uid || isAdmin());
    // }

  }
}
```

---

## 🔑 Key Security Protections Implemented

1. **Strict Admin Sovereignty:** `subhojitpaul26042004@gmail.com` is granted global read/write/delete privileges over all client records and operations.
2. **Self-Promotion Blocked:** Even if a malicious user inspects the API or tries to send an update, they cannot change their own `role` to `admin` or their `status` to `approved` because the `update` and `create` rule explicitly blocks modifying those keys.
3. **Privacy Enforced (No Scanning/Listing):** Standard users can only perform `get` on their own email document. They are barred from doing a `list` query to view or scan other registered users in the database.
4. **Account Deletion Lock:** Only the administrator has the permission to call `delete()` on any document under `/users/{email}`.
