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

No new production files are currently required.

The download functionality should reuse and extend the existing file upload implementation from FILE-01.

If the agent determines that additional files are necessary, it must explain why and request permission before creating them.

The test files specified in section 16 may be created if they do not already exist. Existing test files should be extended where appropriate.

## 11. Files Allowed to Modify

**Backend:**

- `backend/src/main/java/app/daos/FileDAO.java`
- `backend/src/main/java/app/controllers/FileController.java`
- `backend/src/main/java/app/services/FileService.java`
- `backend/src/main/java/app/services/LocalFileStorage.java`
- `backend/src/main/java/app/dtos/FileDTO.java`
- `backend/src/main/java/app/server/Routing.java`

**Frontend:**

- `frontend/src/pages/portal/CustomerChat.jsx`
- `frontend/src/pages/portal/CustomerChat.module.css`
- `frontend/src/pages/portal/portalApi.js`
- `frontend/src/pages/kontrolpanel/MessagesPage.jsx`
- `frontend/src/pages/kontrolpanel/MessagesPage.module.css`

Only modify existing files when necessary for implementing the download functionality.

Before making changes, inspect the existing implementation to determine which files actually require modification.

Do not refactor unrelated functionality or modify existing upload behavior unless strictly necessary.

IT IS ALLOWED TO MODIFY EXISTING TESTS RELATED TO UPLOAD AND DOWNLOAD

## 12. Files Not Allowed to Modify

All files not explicitly listed in sections 10 and 11 are strictly off limits, except for the test files permitted in section 16.

If the agent determines that additional files must be created or modified, it must explain the reason and request permission before proceeding.

Existing authentication, authorization, messaging, and file upload functionality must not be changed unnecessarily.

## 13. Dependencies

Read codebase.

Before implementing, inspect the existing messaging functionality, JWT authentication, DAO patterns, error handling, and frontend chat components.

Reuse existing implementations and conventions wherever possible.

## 14. Expected Behavior / User Flow

The user logs in and navigates to the existing chat through the message tab.

The user can see previously sent messages containing file attachments.

The attachment filenames are displayed as blue, clickable links.

When the user clicks an attachment, the frontend sends a download request to the backend containing the file ID and the existing JWT authentication.

The backend verifies that the authenticated user has permission to download the requested file.

If authorized, the backend retrieves the physical file from storage and returns it as a downloadable response.

The browser starts downloading the file using its original filename.

If the user does not have permission to download the file, the request is rejected and an appropriate error message is displayed.

The same behavior applies to the admin, who can download attachments from all customer conversations they are authorized to access.

Existing file uploads and text-only messages must continue working as before.

## 15. Acceptance Criteria

The following criteria must be satisfied before the ticket can be accepted.

### AC-01 — File Download

- [ ] Authenticated users with `Role.USER` can download attachments from their own chat conversations.
- [ ] Authenticated users with `Role.ADMIN` can download attachments from customer conversations they are authorized to access.
- [ ] Clicking an attachment filename starts a download.
- [ ] The downloaded file preserves its original filename.
- [ ] The downloaded file contains the same content as the originally uploaded file.
- [ ] PDF, PNG, JPG, and JPEG attachments can be downloaded.
- [ ] Files are downloaded rather than opened for preview in the browser.

### AC-02 — File Retrieval

- [ ] The backend retrieves file metadata using the requested file ID.
- [ ] The existing `FileDAO` is used for database operations.
- [ ] The existing `LocalFileStorage` component retrieves the physical file using its `storageKey`.
- [ ] The correct physical file is returned to the requester.
- [ ] The response uses the original filename stored in PostgreSQL.
- [ ] The response includes the appropriate `Content-Type`, `Content-Disposition`, and `Content-Length` headers.
- [ ] Internal storage keys and physical paths are not exposed to the frontend.

### AC-03 — Chat Integration

- [ ] Attachment filenames are displayed as blue, clickable links in the existing chat.
- [ ] Clicking an attachment initiates a download request.
- [ ] Both customers and administrators can download attachments from authorized conversations.
- [ ] Previously uploaded attachments can be downloaded.
- [ ] Multiple attachments belonging to the same message can be downloaded individually.
- [ ] Existing text-only messages continue working.
- [ ] Existing file upload functionality remains unaffected.

### AC-04 — Storage and Database

- [ ] The existing `UploadedFile` entity and its relationships with `User` and `Message` are reused.
- [ ] The existing `UPLOAD_DIR` configuration is reused.
- [ ] Downloading a file does not modify or delete the physical file.
- [ ] Downloading a file does not modify its database metadata.
- [ ] Missing or inaccessible physical files are handled appropriately.
- [ ] No new database tables or entities are introduced unless explicitly approved.

### AC-05 — Security

- [ ] Unauthenticated requests cannot download files.
- [ ] Only authenticated users with `Role.USER` or `Role.ADMIN` can access the download endpoint.
- [ ] The authenticated user is identified through the existing JWT authentication context.
- [ ] The backend verifies authorization before returning file content.
- [ ] Customers cannot download attachments from other customers' conversations.
- [ ] Administrators can only download attachments from conversations they are authorized to access.
- [ ] Files not associated with a chat message cannot be downloaded.
- [ ] Manipulating a file ID does not bypass authorization.
- [ ] Path traversal attempts are prevented.
- [ ] Internal storage information is never exposed to the frontend.

### AC-06 — Frontend

- [ ] Attachment filenames are styled as blue, clickable links.
- [ ] The download functionality is available in both customer and administrator chat interfaces.
- [ ] Clicking an attachment triggers an authenticated request to the backend.
- [ ] The browser downloads the file using its original filename.
- [ ] A loading state is displayed while the download is being prepared.
- [ ] Failed downloads display an understandable error message.
- [ ] Existing frontend styling and component conventions are followed.
- [ ] Existing chat functionality and layout remain unaffected.

---

## 16. Testing Requirements

All tests must follow the existing project's testing frameworks, conventions, and architecture.

The agent must inspect existing test implementations before creating or modifying tests.

Existing FILE-01 tests should be extended where appropriate rather than duplicated.

Tests must not depend on the developer's real upload directory or production data.

### 16.1 FileDAOTest.java

Verify that the existing DAO can retrieve the metadata required for downloading files.

Required tests:

- [ ] Retrieve an uploaded file by its UUID.
- [ ] Retrieve the correct `storageKey` and `originalName`.
- [ ] Verify that the file is associated with the correct user.
- [ ] Verify that the file is associated with the correct message.
- [ ] Handle a file ID that does not exist.
- [ ] Verify that retrieving file metadata does not modify database records.

### 16.2 LocalFileStorageTest.java

Verify physical file retrieval using a temporary test directory.

Required tests:

- [ ] Successfully retrieve an existing physical file.
- [ ] Verify that the retrieved file contains the expected content.
- [ ] Retrieve the correct file using its storage key.
- [ ] Handle a file that does not exist.
- [ ] Handle a file that cannot be accessed or read.
- [ ] Prevent path traversal outside the configured storage directory.
- [ ] Verify that downloading does not modify or delete the stored file.

### 16.3 FileServiceTest.java

Verify business logic, authorization, and coordination between database retrieval and filesystem access.

Required tests:

- [ ] Successfully retrieve a file belonging to the authenticated customer's conversation.
- [ ] Successfully retrieve an attachment sent by the admin to that customer.
- [ ] Allow an authorized administrator to retrieve attachments from customer conversations.
- [ ] Reject a customer attempting to download another customer's attachment.
- [ ] Reject an administrator attempting to access a conversation they are not authorized to access.
- [ ] Reject attempts to download files that are not associated with a message.
- [ ] Handle invalid or nonexistent file IDs.
- [ ] Verify that authorization occurs before accessing the physical file.
- [ ] Handle filesystem retrieval failures.
- [ ] Verify that the original filename and content type are preserved.

### 16.4 FileControllerTest.java

Verify the download endpoint and HTTP responses.

Required tests:

- [ ] `GET /api/files/{fileId}` successfully downloads an authorized file.
- [ ] A successful download returns `200 OK`.
- [ ] The response contains the correct binary file content.
- [ ] The response contains the correct `Content-Type`.
- [ ] The response contains `Content-Disposition: attachment` with the original filename.
- [ ] The response contains the correct `Content-Length`.
- [ ] Requests without authentication return `401 Unauthorized`.
- [ ] Requests without the required role are rejected.
- [ ] Malformed file IDs return `400 Bad Request`.
- [ ] Unauthorized file access returns the error status defined in the API contract.
- [ ] Nonexistent files return `404 Not Found`.
- [ ] Files without an associated message cannot be downloaded.
- [ ] Unexpected filesystem or database failures return an appropriate error without exposing internal implementation details.

### 16.5 Chat Integration Tests

Verify that downloads integrate correctly with the existing messaging functionality.

Required tests:

- [ ] A customer can download an attachment they previously uploaded to their conversation.
- [ ] A customer can download an attachment sent by the admin.
- [ ] An administrator can download an attachment sent by a customer.
- [ ] An administrator can download an attachment they sent in an authorized customer conversation.
- [ ] Multiple attachments in one message can be downloaded individually.
- [ ] Attachments cannot be downloaded from unauthorized conversations.
- [ ] Previously uploaded attachments remain accessible after reopening the chat.
- [ ] Existing text-only messages continue working.
- [ ] Existing file upload functionality continues working.

### 16.6 Frontend Tests

Verify the download functionality in both customer and administrator chat interfaces.

Required tests:

- [ ] Attachment filenames are displayed as blue, clickable links.
- [ ] Clicking an attachment sends a request containing the correct file ID.
- [ ] The request includes the existing JWT authentication.
- [ ] A successful response triggers a browser download.
- [ ] The downloaded file uses its original filename.
- [ ] PDF and image attachments download instead of opening for preview.
- [ ] A loading state is displayed while preparing the download.
- [ ] Failed downloads display an understandable error message.
- [ ] Clicking an attachment does not accidentally send a chat message.
- [ ] Existing upload and messaging functionality continues working.

### 16.7 Test Isolation

- [ ] Filesystem tests use temporary directories.
- [ ] Tests do not modify or delete real uploaded files.
- [ ] Database tests use the existing test database configuration.
- [ ] Tests clean up their created data and temporary files.
- [ ] Tests can be executed repeatedly without depending on previous test runs.

---

## 17. Definition of Done

FILE-02 is considered complete when all the following conditions are satisfied.

### Implementation

- [ ] Customers and administrators can download attachments through their existing chat interfaces.
- [ ] Clicking an attachment filename starts the download.
- [ ] Downloaded files preserve their original filenames and contents.
- [ ] PDF, PNG, JPG, and JPEG attachments can be downloaded.
- [ ] The backend retrieves the correct physical file using its internal storage key.
- [ ] Only authorized users can download attachments.
- [ ] Existing upload functionality and text-only messaging remain functional.

### Architecture and Code Quality

- [ ] The implementation follows `AGENTS.md`, `ARCHITECTURE.md`, and `CODESTANDARD.md`.
- [ ] The existing controller → service → DAO architecture is respected.
- [ ] Database operations are handled through the DAO.
- [ ] Download authorization and business logic are handled through `FileService`.
- [ ] Filesystem operations are handled through `LocalFileStorage`.
- [ ] Existing FILE-01 functionality is reused wherever possible.
- [ ] No unnecessary dependencies, refactoring, or unrelated functionality have been introduced.
- [ ] Only files explicitly permitted by the ticket have been created or modified.
- [ ] Existing project naming conventions and coding patterns have been followed.

### Security

- [ ] JWT authentication and role-based access are enforced.
- [ ] File-specific authorization is verified before returning file content.
- [ ] Customers cannot download attachments belonging to other customers' conversations.
- [ ] Administrators can only download attachments from authorized conversations.
- [ ] Unattached files cannot be downloaded.
- [ ] Invalid or manipulated file IDs cannot bypass authorization.
- [ ] Internal storage paths and storage keys are never exposed.
- [ ] Unauthorized requests are rejected without returning file content.

### Testing and Verification

- [ ] All relevant unit tests pass.
- [ ] All relevant integration tests pass.
- [ ] Backend builds successfully.
- [ ] Frontend builds successfully.
- [ ] Existing FILE-01 tests continue passing.
- [ ] The download functionality has been manually verified in both customer and administrator chat interfaces.
- [ ] Previously uploaded files can be downloaded successfully.
- [ ] Existing file uploads and text-only messages remain functional.
- [ ] No new linting or compilation errors have been introduced.

### Final Delivery

- [ ] The agent provides a summary of implemented functionality.
- [ ] The agent lists all created and modified files.
- [ ] The agent reports which tests and build commands were executed and their results.
- [ ] Any limitations, unresolved issues, or deviations from the specification are documented.
- [ ] The agent does not claim completion if required acceptance criteria remain unsatisfied.
