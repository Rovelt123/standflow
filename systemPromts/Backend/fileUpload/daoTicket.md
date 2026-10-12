# FILE-01 — Upload a File

## 1. Objective

The objective of this ticket is to create a file upload feature for the admin and user through the existing chat.

When the work is done both the admin and the user should be able to upload up to 3 files at a time, with a maximum size of 1 MiB (1,048,576 bytes) per file. The file formats allowed are `.pdf`, `.png`, `.jpg`, and `.jpeg`.

## 2. Context / Existing Implementation

Read the current system's files to create an understanding of the codebase. The existing file that you should be working with is `UploadedFile.java`, which is the JPA entity. Look at the other DAOs to recreate their pattern to the best of your ability.

The existing chat system is between the user and admin. Files belong to users but must also be associated with the corresponding chat messages.

## 3. User Stories

As a user, I want to be able to upload up to 3 files at a time through the chat, so that the admin can see that I attached files (SHOULD NOT BE ABLE TO DOWNLOAD YET, ONLY UPLOAD).

As an admin, I want to be able to upload up to 3 files at a time through the chat, so that the user can see that I attached files (SHOULD NOT BE ABLE TO DOWNLOAD YET, ONLY UPLOAD).

## 4. Scope

You must create the feature to upload a maximum of 3 files per chat message with a maximum file size of 1 MiB per file.

The user and admin must have a button on their existing chat where they can press upload, be informed of the valid file formats, size, and amount of files.

The user must be able to select up to 3 files simultaneously. The files must be uploaded together in a single request and attached to the corresponding chat message.

## 5. Out of Scope

The ability to download files for admin and user is out of scope currently.

The ability to preview or view the content of uploaded files is out of scope.

The ability for users or administrators to delete already uploaded attachments is out of scope.

Persisting files across deployments is out of scope. I will have a folder in this project that will act as the holder for files.

Internal cleanup of failed uploads is allowed where necessary to prevent orphaned files.

## 6. Functional Requirements

- Supports uploading up to 3 files at once.
- Files can be no larger than 1 MiB per file.
- Only `.pdf`, `.png`, `.jpg`, and `.jpeg` are supported.
- Button on frontend to upload.
- Information on frontend button to display the limitations.
- Users must be able to select multiple files at once.
- Selected filenames must be displayed before sending.
- Users must be able to remove selected files before sending.
- All selected files must be uploaded together in one request.
- Files must be associated with the correct chat message.
- The uploaded filenames in the chat should be visible but not clickable nor viewable.
- Existing text-only messages must continue working.

## 7. Technical Requirements

Before coding read: `AGENTS.md`, `ARCHITECTURE.md`, `CODESTANDARD.md`.

Read the codebase before coding, refactoring, creating or deleting anything.

### 7.1 Backend

The DAO is strictly for handling transactions with the database.

The controller should handle HTTP requests and responses and delegate business logic to the service, possibly using DTOs.

The service is where things get created, deleted and handled. It uses the DAO and contains the business logic.

The backend must support receiving up to 3 files in the same multipart request.

The existing messaging functionality must be extended so uploaded files can be associated with the correct chat message.

### 7.2 Frontend

Keep the current style of components and CSS on the frontend.

Create a button next to the existing Send button and a place to make the limitations visible for file uploading.

The user must be able to select up to 3 files simultaneously through the file picker.

Selected filenames must appear before sending, and the user must be able to remove selected files.

When the user presses Send, the selected files must be uploaded together and associated with the sent message.

After sending, filenames should appear in the chat without being clickable, downloadable or previewable.

### 7.3 Database

JPA entity `UploadedFile.java` is already created and explains most of the relationships involved.

The user owns the files, but the files are attached to messages.

One message may have a maximum of 3 uploaded files.

The actual files must be stored on the filesystem, while PostgreSQL stores their metadata.

### 7.4 File Storage

For now the file storage is a directory on this project:

`UPLOAD_DIR=C:\Users\linus\OneDrive\Coding\newStandflow\standflow\uploads`

Path configuration should be done using an environment variable named `UPLOAD_DIR`. Then use a path converter. Make it fail if the path isn't valid.

The backend should generate names automatically. The application should validate the content before generating the final filename for the file.

The files need to be stored and a separate class should be created just for this.

For example:

| Scenario | Expected behavior |
|---|---|
| Storage directory doesn't exist | Create it at startup |
| Storage directory isn't writable | Fail startup with a clear error |
| File already exists | Never overwrite it |
| Upload contains more than 3 files | Reject the entire upload |
| Any file exceeds 1 MiB | Reject the entire upload and remove temporary files |
| Any file has an invalid format | Reject the entire upload |
| File cannot be written | Return an error; don't save metadata |
| Database save fails | Remove newly stored files |
| One file in the request fails | Reject the entire upload and clean up files created by that request |
| Message creation fails after upload | Keep unattached files pending for controlled retry or cleanup |

## 8. Security and Validation Requirements

Only authenticated and authorized users through JWT must be able to upload files to the chat between user and admin.

There should be backend validation of the files uploaded so that they are not empty, too large, the wrong format, or exceed the maximum amount.

The backend must validate each file individually.

The request must contain between 1 and 3 files.

Each file must be no larger than 1 MiB (1,048,576 bytes).

The actual content must match the allowed file format. Client-provided MIME types must not be trusted.

Breaking any of these validations should reject the entire request and display an error message to the user/admin.

The authenticated user must be determined through the existing JWT authentication context.

Users must not be able to attach another user's files to their messages.

The backend must enforce multipart request size limits and prevent path traversal.

## 9. API Contract

The file upload functionality must integrate with the existing Javalin REST API and JWT authentication system.

The frontend must send uploaded files using `multipart/form-data`.

The backend must support receiving between 1 and 3 files in one request.

The backend must validate all uploaded files, identify the authenticated user, store the files on the filesystem, and persist their metadata in PostgreSQL.

The upload endpoint must return a JSON response containing the uploaded files' metadata.

The internal storage keys and physical file paths must not be exposed to the frontend.

### 9.1 Endpoint

**HTTP Method:** `POST`

**Endpoint:** `/api/files`

**Authentication:** Required (JWT)

**Allowed roles:** `Role.USER`, `Role.ADMIN`

**Purpose:**

Allow an authenticated customer or administrator to upload between 1 and 3 PDF, PNG, or JPEG files simultaneously.

All files must be uploaded in one multipart request.

The endpoint must identify the uploader through the existing authentication context rather than accepting a user ID from the request.

The endpoint must delegate validation, storage, and database persistence to the appropriate service.

**Chat Integration:**

The existing messaging endpoints must support associating uploaded files with messages:

- `POST /api/messages`
- `POST /api/messages/threads/{customerId}`

After the files have been uploaded successfully, the frontend must send their returned IDs together with the chat message.

The backend must verify ownership and associate the files with the newly created message.

The association must be completed as part of this ticket.

### 9.2 Request Format

**Content-Type:** `multipart/form-data`

**Headers:**

- `Authorization: Bearer <token>`
- `Content-Type: multipart/form-data` (generated automatically by the browser)

**Request fields:**

| Field | Type | Required | Description |
|---|---|---|---|
| `files` | Multiple binary files | Yes | Between 1 and 3 PDF, PNG, or JPEG files |

The frontend must append each selected file using the same multipart field name: `files`.

**Validation requirements:**

- The request must contain between 1 and 3 files.
- No file must be empty.
- Each file must not exceed 1 MiB (1,048,576 bytes).
- Only PDF, PNG, JPG, and JPEG are allowed.
- The actual file content must match the validated file type.
- Every file in the request must pass validation.
- If one file is invalid, the entire request must be rejected.
- The authenticated user must be identified through `ctx.attribute("user")`.

No `userId`, `storageKey`, or filesystem path may be supplied by the frontend.

**Message association request:**

The existing message endpoints must support an optional `attachmentIds` array.

Example:

```json
{
  "subject": "Documentation",
  "body": "Here are the requested documents.",
  "attachmentIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
    "8c93a120-9dad-41d1-80b4-00c04fd430c8"
  ]
}
```

The backend must verify that:

- A maximum of 3 attachment IDs are provided.
- All files belong to the authenticated sender.
- The IDs are valid and unique.
- The files are not already attached to another message.
- All files exist.

Existing text-only messages must continue working without `attachmentIds`.

### 9.3 Response Format

**Successful response:** `201 Created`

The response must contain the uploaded files' public metadata.

Example:

```json
{
  "files": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "originalName": "document.pdf",
      "contentType": "application/pdf",
      "size": 245760,
      "uploadedAt": "2026-10-12T08:30:00Z"
    },
    {
      "id": "6ba7b810-9dad-41d1-80b4-00c04fd430c8",
      "originalName": "image.png",
      "contentType": "image/png",
      "size": 512000,
      "uploadedAt": "2026-10-12T08:30:00Z"
    },
    {
      "id": "8c93a120-9dad-41d1-80b4-00c04fd430c8",
      "originalName": "photo.jpg",
      "contentType": "image/jpeg",
      "size": 750000,
      "uploadedAt": "2026-10-12T08:30:00Z"
    }
  ]
}
```

The response must not contain:

- Physical storage paths
- Internal storage keys
- File binary data
- Sensitive user information

The returned file IDs must be used to associate the uploaded files with the chat message.

The existing message response must include attachment metadata so filenames can be displayed in the chat.

### 9.4 Error Handling

The endpoint must use the application's existing exception and error-handling architecture.

| Status | Scenario |
|---|---|
| `400 Bad Request` | Missing files, empty files, more than 3 files, invalid attachment IDs, or invalid multipart request |
| `401 Unauthorized` | Missing or invalid authentication |
| `403 Forbidden` | Authenticated user lacks the required role or permission |
| `404 Not Found` | Referenced attachment does not exist |
| `413 Content Too Large` | Any file exceeds the configured upload limit |
| `415 Unsupported Media Type` | Unsupported or invalid file format |
| `500 Internal Server Error` | Unexpected storage or database failure |

Error responses must follow the existing application's error response structure.

**Error handling requirements:**

- Validation errors must return an appropriate HTTP status code.
- Internal filesystem paths and technical exception details must not be exposed.
- If any file in the request is invalid, the entire upload must fail.
- Failed uploads must not leave partially saved files or metadata.
- If database persistence fails after storing files, the newly stored files must be cleaned up.
- Temporary files must be removed after failed uploads.
- Message creation and attachment association must not leave partially associated messages.
- Successfully uploaded files that are not attached to a message must be handled as pending uploads and cleaned up after a defined expiration period.
- Unexpected errors must be logged on the backend.

## 10. Files to Create

- `backend/src/main/java/app/daos/FileDAO.java`
- `backend/src/main/java/app/controllers/FileController.java`
- `backend/src/main/java/app/services/FileService.java`
- `backend/src/main/java/app/services/LocalFileStorage.java`
- `backend/src/main/java/app/dtos/FileDTO.java`
- `backend/src/main/java/app/services/FileValidator.java`
- `frontend/src/pages/portal/FileUpload.jsx`
- `frontend/src/pages/portal/FileUpload.module.css`

The test files specified in section 16 may also be created.

## 11. Files Allowed to Modify

- `backend/src/main/java/app/entities/UploadedFile.java`
- `frontend/src/pages/portal/CustomerChat.jsx`
- `frontend/src/pages/portal/CustomerChat.module.css`
- `frontend/src/pages/portal/portalApi.js`
- `frontend/src/App.jsx`
- `frontend/src/styles/tokens.css`
- `frontend/src/pages/kontrolpanel/MessagesPage.jsx`
- `frontend/src/pages/kontrolpanel/MessagesPage.module.css`
- `backend/src/main/java/app/server/Routing.java`
- `backend/src/main/java/app/configs/EntityRegistry.java`
- `backend/src/main/java/app/controllers/MessageController.java`
- `backend/src/main/java/app/controllers/UserController.java`
- `backend/src/main/java/app/services/ConversationService.java`
- `backend/src/main/java/app/dtos/MessageDTO.java`
- `backend/src/main/java/app/mappers/MessageMapper.java`
- `backend/src/main/java/app/daos/MessageDAO.java`

Only modify existing files when necessary for implementing the ticket.

## 12. Files Not Allowed to Modify

All other files that are not mentioned above are strictly off limits. If necessary to modify other files, ask for permission.

## 13. Dependencies

Read codebase.

Before implementing, inspect the existing messaging functionality, JWT authentication, DAO patterns, error handling, and frontend chat components.

Reuse existing implementations and conventions wherever possible.

## 14. Expected Behavior / User Flow

User logs in and presses on the chat feature.

The user now has a button next to the Send button that allows file uploading.

The user presses it and a window appears informing them of the limits: allowed file formats, maximum 1 MiB per file, and maximum 3 files.

The user can then click on a link or button that opens the operating system's file picker.

The user chooses up to 3 files at the same time and presses Open.

The selected filenames are now displayed in the chat input area.

The user may remove selected files before sending.

When the user presses Send, the frontend uploads all selected files together in one request.

The backend validates each file and the number of files.

If all files pass validation, the backend saves them to the filesystem and persists their metadata in PostgreSQL.

The frontend then sends the chat message together with the returned attachment IDs.

The backend associates the files with the correct message.

The user and admin will now see the filenames attached to the message in the chat.

The filenames must not be clickable, viewable, or downloadable.

If any file fails validation or uploading, the message must not be sent with incomplete attachments. An error message must be displayed.

The same behavior applies to the admin.

Text-only messages must continue working without file attachments.

## 15. Acceptance Criteria

The following criteria must be satisfied before the ticket can be accepted.

### AC-01 — File Upload

- [ ] Authenticated users with `Role.USER` can upload files through their existing chat interface.
- [ ] Authenticated users with `Role.ADMIN` can upload files through their existing chat interface.
- [ ] The upload functionality is accessible through a button next to the existing Send button.
- [ ] Clicking the upload button opens a dialog displaying the supported file formats, maximum file size, and maximum number of attachments.
- [ ] The user can select up to 3 files simultaneously using the operating system's file picker.
- [ ] All selected files are sent together in one multipart request.
- [ ] Successfully uploaded files are stored in the configured filesystem directory.
- [ ] Uploaded file metadata is persisted in PostgreSQL.
- [ ] Every uploaded file is associated with the authenticated user.

### AC-02 — File Restrictions

- [ ] The system accepts `.pdf`, `.png`, `.jpg`, and `.jpeg` files.
- [ ] Each file must not exceed 1 MiB (1,048,576 bytes).
- [ ] A maximum of three files can be uploaded in one request and attached to a single chat message.
- [ ] A request containing four or more files is rejected.
- [ ] Empty files are rejected.
- [ ] Unsupported file formats are rejected.
- [ ] Files with misleading extensions or invalid content are rejected.
- [ ] File size, format, and attachment-count restrictions are enforced by the backend, not only the frontend.
- [ ] If any file in the request is invalid, the entire upload is rejected.
- [ ] Invalid uploads display an understandable error message.

### AC-03 — Chat Integration

- [ ] Selected files are displayed by filename in the chat interface.
- [ ] Successfully sent messages display their associated attachment filenames.
- [ ] Attachments are visible to both the sender and recipient in the correct conversation.
- [ ] Attachments are not clickable, downloadable, or previewable.
- [ ] Text-only messages continue functioning as before.
- [ ] Uploading multiple files does not create duplicate chat messages.
- [ ] Files cannot be associated with messages belonging to another user or conversation.

### AC-04 — Storage and Database

- [ ] The storage directory is configured using the `UPLOAD_DIR` environment variable.
- [ ] The storage directory is created at startup if it does not exist.
- [ ] The application fails startup with a clear error if the storage directory cannot be accessed or written to.
- [ ] Every uploaded file receives a unique UUID-based storage filename.
- [ ] The original filename is preserved in PostgreSQL.
- [ ] Existing files are never overwritten.
- [ ] Uploaded files are associated with the correct `User` entity.
- [ ] Files attached to messages are associated with the correct `Message` entity.
- [ ] Failed uploads do not leave invalid database records or orphaned physical files.
- [ ] Internal storage paths and storage keys are never exposed to the frontend.

### AC-05 — Security

- [ ] Unauthenticated requests cannot upload files.
- [ ] Only `Role.USER` and `Role.ADMIN` can access the upload endpoint.
- [ ] The authenticated user is identified through the existing JWT authentication context.
- [ ] Users cannot specify or manipulate the uploader ID.
- [ ] Users cannot attach another user's uploaded files to their messages.
- [ ] Files are stored outside publicly accessible frontend directories.
- [ ] Path traversal attempts are prevented.
- [ ] Upload request sizes are limited to prevent excessive resource consumption.
- [ ] Validation and storage errors are handled through the existing error-handling architecture.

### AC-06 — Frontend

- [ ] The new upload functionality follows the existing frontend styling and component conventions.
- [ ] The upload button is available in both customer and administrator chat interfaces.
- [ ] Users are informed of supported file formats, the 1 MiB per-file size limit, and the three-file limit.
- [ ] Users can select up to 3 files in one file-selection operation.
- [ ] Selected filenames are displayed before sending.
- [ ] Users can remove selected files before sending the message.
- [ ] The frontend displays an upload loading state.
- [ ] Failed uploads display an error without incorrectly indicating success.
- [ ] Existing chat functionality and layout remain unaffected.

---

## 16. Testing Requirements

All tests must follow the existing project's testing frameworks, conventions, and architecture.

The agent must inspect existing test implementations before creating new tests.

Tests must not depend on the developer's real upload directory or production data.

### 16.1 FileDAOTest.java

Verify database persistence and retrieval of uploaded file metadata.

Required tests:

- [ ] Successfully persist an `UploadedFile` entity.
- [ ] Retrieve an uploaded file by its UUID.
- [ ] Verify the uploaded file is associated with the correct user.
- [ ] Verify the uploaded file can be associated with a message.
- [ ] Verify that multiple files can belong to the same user.
- [ ] Verify that multiple files can belong to the same message.
- [ ] Verify that duplicate storage keys are rejected.
- [ ] Verify that required database fields cannot be null.

### 16.2 LocalFileStorageTest.java

Verify physical file storage operations using a temporary test directory.

Required tests:

- [ ] Create the upload directory when it does not exist.
- [ ] Save a valid file successfully.
- [ ] Verify the file exists at the expected storage location.
- [ ] Generate unique storage filenames.
- [ ] Prevent existing files from being overwritten.
- [ ] Handle invalid or inaccessible storage paths.
- [ ] Handle filesystem write failures.
- [ ] Clean up temporary files after failed operations.
- [ ] Verify that path traversal cannot access files outside the configured directory.
- [ ] Verify cleanup of files after a failed multi-file upload.

### 16.3 FileValidatorTest.java

Verify file validation independently of the controller and database.

Required tests:

- [ ] Accept a valid PDF file.
- [ ] Accept a valid PNG file.
- [ ] Accept a valid JPG/JPEG file.
- [ ] Reject unsupported file formats.
- [ ] Reject empty files.
- [ ] Accept a file exactly 1,048,576 bytes in size.
- [ ] Reject a file larger than 1,048,576 bytes.
- [ ] Reject a file whose extension does not match its actual content.
- [ ] Reject malformed files.
- [ ] Verify that client-provided MIME types are not trusted.
- [ ] Verify that the validation process does not allow unbounded memory consumption.
- [ ] Accept a request containing 1, 2, or 3 valid files.
- [ ] Reject a request containing more than 3 files.
- [ ] Reject an entire request when one of the selected files is invalid.

### 16.4 FileServiceTest.java

Verify business logic and coordination between validation, filesystem storage, and database persistence.

Required tests:

- [ ] Successfully upload one valid file.
- [ ] Successfully upload three valid files in one operation.
- [ ] Associate all uploaded files with the authenticated user.
- [ ] Generate and persist the correct metadata for each file.
- [ ] Verify that the original filenames are preserved.
- [ ] Reject invalid files before permanent storage.
- [ ] Reject attempts to associate another user's file with a message.
- [ ] Enforce a maximum of three attachments per message.
- [ ] Reject duplicate attachment IDs within the same message.
- [ ] Handle filesystem failures without persisting invalid metadata.
- [ ] Remove all newly stored files when database persistence fails.
- [ ] Ensure failed uploads do not leave temporary files behind.
- [ ] Verify that a failed multi-file upload does not leave partially successful results.

### 16.5 FileControllerTest.java

Verify the upload endpoint and HTTP responses.

Required tests:

- [ ] `POST /api/files` accepts one valid multipart file.
- [ ] `POST /api/files` accepts three valid multipart files in one request.
- [ ] A successful upload returns `201 Created`.
- [ ] The response contains an array of uploaded file metadata.
- [ ] The response does not expose internal storage keys or filesystem paths.
- [ ] Requests without authentication return `401 Unauthorized`.
- [ ] Requests without the required role are rejected.
- [ ] Requests without files return `400 Bad Request`.
- [ ] Requests containing four files return `400 Bad Request`.
- [ ] Empty files return `400 Bad Request`.
- [ ] Oversized files return `413 Content Too Large`.
- [ ] Unsupported formats return `415 Unsupported Media Type`.
- [ ] Malformed multipart requests are handled correctly.
- [ ] A request containing two valid files and one invalid file is rejected completely.
- [ ] Unexpected storage or database failures return an appropriate server error without exposing internal implementation details.

### 16.6 Chat Integration Tests

Verify that uploads integrate correctly with the existing messaging functionality.

Required tests:

- [ ] A customer can send a chat message containing one attachment.
- [ ] A customer can send a chat message containing three attachments uploaded in one request.
- [ ] An administrator can send a chat message containing attachments.
- [ ] Both participants can see the filenames of attachments in their conversation.
- [ ] A message with four attachments is rejected.
- [ ] An unauthorized attachment ID is rejected.
- [ ] A failed attachment operation does not result in a successfully sent message with missing attachments.
- [ ] Existing text-only messages continue working.
- [ ] Attachments are not exposed in other customers' conversations.
- [ ] A multi-file upload results in one chat message, not multiple messages.

### 16.7 Frontend Tests

Verify the upload interface in both customer and administrator chat views.

Required tests:

- [ ] The upload button is displayed next to the Send button.
- [ ] Clicking the button opens the upload dialog.
- [ ] The dialog displays supported formats and upload limits.
- [ ] The operating system's file picker can be opened.
- [ ] The user can select up to 3 files simultaneously.
- [ ] Selected filenames are displayed.
- [ ] Users can remove selected files before sending.
- [ ] The frontend rejects selections exceeding three attachments.
- [ ] The frontend displays errors for oversized and unsupported files.
- [ ] All selected files are submitted together in one request.
- [ ] Successful uploads display the correct filenames in the chat.
- [ ] Attachments cannot be clicked, downloaded, or previewed.
- [ ] Existing message sending functionality continues working.

### 16.8 Test Isolation

- [ ] Filesystem tests use temporary directories.
- [ ] Tests do not modify or delete real user-uploaded files.
- [ ] Database tests use the existing test database configuration.
- [ ] Tests clean up their created data and temporary files.
- [ ] Tests can be executed repeatedly without depending on previous test runs.

---

## 17. Definition of Done

FILE-01 is considered complete when all the following conditions are satisfied.

### Implementation

- [ ] All functional requirements within the ticket's scope are implemented.
- [ ] Customers and administrators can upload files through their existing chat interfaces.
- [ ] Up to three files can be uploaded simultaneously in one multipart request.
- [ ] A maximum of three files per message is enforced.
- [ ] The 1 MiB per-file size limit is enforced.
- [ ] Only supported file formats are accepted.
- [ ] Uploaded files are stored successfully on the local filesystem.
- [ ] File metadata is persisted correctly in PostgreSQL.
- [ ] Files are associated with the correct user and chat message.
- [ ] Attachment filenames are visible in the chat without download or preview functionality.

### Architecture and Code Quality

- [ ] The implementation follows `AGENTS.md`, `ARCHITECTURE.md`, and `CODESTANDARD.md`.
- [ ] The existing controller → service → DAO architecture is respected.
- [ ] Database operations are handled through the DAO.
- [ ] Business logic is implemented in the service layer.
- [ ] Filesystem operations are separated into a dedicated storage component.
- [ ] No unnecessary dependencies, refactoring, or unrelated functionality have been introduced.
- [ ] Only files explicitly permitted by the ticket have been created or modified.
- [ ] Existing project naming conventions and coding patterns have been followed.

### Security

- [ ] Authentication and authorization are enforced.
- [ ] Backend validation covers file size, file type, content, and attachment count.
- [ ] The entire upload is rejected if any selected file fails validation.
- [ ] Storage paths and filenames are handled securely.
- [ ] Internal storage information is not exposed to clients.
- [ ] Failed uploads are handled safely and do not leave inconsistent data.

### Testing and Verification

- [ ] All relevant unit tests pass.
- [ ] All relevant integration tests pass.
- [ ] Backend builds successfully.
- [ ] Frontend builds successfully.
- [ ] Existing tests continue passing.
- [ ] The feature has been manually verified in both customer and administrator chat interfaces.
- [ ] Existing text-only messaging remains functional.
- [ ] No new linting or compilation errors have been introduced.

### Final Delivery

- [ ] The agent provides a summary of implemented functionality.
- [ ] The agent lists all created and modified files.
- [ ] The agent reports which tests and build commands were executed and their results.
- [ ] Any limitations, unresolved issues, or deviations from the specification are documented.
- [ ] The agent does not claim completion if required acceptance criteria remain unsatisfied.
