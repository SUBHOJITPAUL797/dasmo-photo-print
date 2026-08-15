# Custom Project Instructions & Guidelines

These rules MUST be strictly followed by all development agents in all future turns. Do NOT modify, bypass, or rename these rules.

## 1. App Identity
* **App Name:** `DASMO PHOTO PRINT`
* **Underlying Package Name:** `dasmocybercafe.photoprint.subhojit`
* **Rules on Renaming:** **NEVER** change the app name or rename references to "One Unified Account" or any other name. The name must always remain `DASMO PHOTO PRINT`.

## 2. Firebase Database Security Rules & Roles
* **Authorized Administrator Email:** `subhojitpaul26042004@gmail.com`
* **Security Model:** Only the authorized administrator account has full read/write/delete access to all user records.
* **Standard Users Constraints:**
  - Standard users can only read their own specific user documents.
  - Standard users cannot list/scan other users.
  - Standard users can register themselves only as `role: "user"` and `status: "pending"`.
  - Standard users are strictly blocked from self-promoting to admin or changing their status/role or subscription expiry.
  - Standard users are prohibited from deleting data.

## 3. Persistent Database Rules (Firestore)
Ensure that the Firebase Firestore security rules matches the specification below to secure BOTH apps (`DASMO PHOTO PRINT` and `DASMO SCANNER`) running under the same Firebase console:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isAuthenticated() {
      return request.auth != null;
    }

    function isUserEmail(email) {
      return isAuthenticated() && request.auth.token.email.toLowerCase() == email.toLowerCase();
    }

    function isAdmin() {
      return isAuthenticated() && request.auth.token.email.toLowerCase() == 'subhojitpaul26042004@gmail.com';
    }

    // DASMO PHOTO PRINT (users collection)
    match /users/{email} {
      allow read, write: if isAdmin();
      allow get: if isUserEmail(email);
      allow create: if isUserEmail(email) 
        && request.resource.data.role == 'user'
        && request.resource.data.status == 'pending';
      allow update: if isUserEmail(email)
        && request.resource.data.role == resource.data.role
        && request.resource.data.status == resource.data.status
        && (!request.resource.data.diff(resource.data).affectedKeys().hasAny(['role', 'status', 'expiryTimestamp']));
      allow delete: if isAdmin();
    }
  }
}
```
