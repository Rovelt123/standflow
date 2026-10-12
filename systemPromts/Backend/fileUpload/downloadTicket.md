# FILE-02 — Download a File

## 1. Objective
The objective of this ticket is to implement file downloads through the existing chat system for users and administrators. The upload implementation has already been implemented and the ticket can be viewed: C:\Users\linus\OneDrive\Coding\newStandflow\standflow\systemPromts\Backend\fileUpload\uploadTicket.md

The user and admin should be able to download the attached file(s) within the chat system. This means that the frontend needs to display something that is clickable and will download when clicked. This means that there needs to be security measures to ensure that these files can only be downloaded between admin and user. 

## 2. Context / Existing Implementation
C:\Users\linus\OneDrive\Coding\newStandflow\standflow\systemPromts\Backend\fileUpload\uploadTicket.md has been implemented. Right now it validates the pdf uploaded it stores the files in a folder outside the database and the database holds the metadata and the storage key. The upload button is implemented on the frontend. There is the JPA entity "UploadFile" which creates the database entity "uploaded_files". 
uploaded_files:
- id
- contenttype
- originalname
- size
- storagekey
- uploadedat
- message_id
- user_id

## 3. User Stories
As a user I want to be able to click on the attached file in the existing chat system and the file should download to my pc. (UPLOAD ALREADY IMPLEMENTED, CHECK OUT CODEBASE)

As an admin I want to be able to click on the attached file in the existing chat system and file should download to my pc. (UPLOAD ALREADY IMPLEMENTED, CHECK OUT CODEBASE)

## 4. Scope
You must create the feature to be able to start a download to the local pc when a click on the attached file happens.

## 5. Out of Scope
Previewing files in the browser is out of scope

## 6. Functional Requirements

The user and admin should download the attachment(s) when they are clicked. For the user the only accessible files are their own attachments and the admin attachments. 
For the admin the accessible files include every chats attached files that the admin has access to. 
The attached file should preserve the original file name.

- clickable attachfiles that start downloading when clicked

## 7. Technical Requirements

Before coding read: `AGENTS.md`, `ARCHITECTURE.md`, `CODESTANDARD.md`.

Read the codebase before coding, refactoring, creating or deleting anything.

### 7.1 Backend

The backend must verify that the authenticated user has permission to download the requested file before retrieving and returning its content.

The existing JWT authentication system must identify the requesting user.

The FileController should receive the download request, retrieve the authenticated user's identity from the security context, and delegate the request to FileService.

The FileService is responsible for the authorization logic and must verify that:

The requested file exists in the database.

The file is associated with an existing chat message.

A user can only download attachments from their own conversation with the admin.

An admin can download attachments from customer conversations they are authorized to access.

Files belonging to other users' conversations cannot be downloaded by unauthorized users.

The FileDAO should retrieve the file metadata and its relationships with the user and message from the database.

After authorization succeeds, FileService should use the existing LocalFileStorage component to retrieve the physical file using its internal storage key.

The FileController should return the file as a downloadable response using the original filename stored in the database.

If authorization fails, the backend must reject the request without exposing the file's content or internal storage information.

### 7.2 Frontend
It should stay as it is now when the plain text, but it should be a blue link that you can click on (the entire text) and when clicked it sends the call back to the backend. This is how I want both for the admin and user.

### 7.3 Database

The existing `UploadedFile` entity and its relationships with `User` and `Message` must be reused.

The `FileDAO` must retrieve the requested file's metadata from PostgreSQL using the file ID provided in the download request.

The `FileService` must use the retrieved information to verify that the authenticated user has permission to download the attachment.

The `storageKey` must be used to locate the physical file on the server, while `originalName` must be used as the filename when downloading.

The download functionality must not modify or delete the existing file metadata or database relationships.

### 7.4 File Storage

The existing `LocalFileStorage` class and `UPLOAD_DIR` environment variable must be reused for downloading files.

The `FileService` must use the file's `storageKey`, retrieved from the database, to request the physical file from `LocalFileStorage`.

The `LocalFileStorage` class is responsible for locating and reading the requested file from the configured storage directory.

The file must only be retrieved after the `FileService` has verified that the authenticated user has permission to download it.

The download functionality must not modify, rename, or delete the original stored file.

If the physical file cannot be found or accessed, the backend must return an appropriate error response.

Internal storage paths and storage keys must never be exposed to the frontend.

## 8. Security and Validation Requirements

Reuse the existing security and validation mechanisms in the codebase, including JWT authentication and role-based access control.

Only authenticated users with `Role.USER` or `Role.ADMIN` should be able to access the download endpoint.

The `FileService` must verify that the authenticated user has permission to download the requested file.

- A user can only download attachments associated with their own conversation with the admin.
- An admin can download attachments from customer conversations they are authorized to access.
- Users must not be able to download attachments belonging to other customers' conversations.
- Files that are not associated with a chat message must not be downloadable.

The backend must validate the provided file ID and verify that the requested file exists.

The backend must prevent unauthorized access to files through manipulated file IDs or filesystem paths.

Internal storage keys and physical file paths must never be exposed to the frontend.

If authentication or authorization fails, the download must be rejected without returning the file content.

## 9 API contract

The file download functionality must integrate with the existing Javalin REST API and JWT authentication system.


### 9.1 Endpoint

**HTTP Method:** `GET`

**Endpoint:** `/api/files/{fileId}`

**Authentication:** Required (JWT)

**Allowed roles:** `Role.USER`, `Role.ADMIN`

**Purpose:**

Allow an authenticated user or administrator to download an attachment from a chat conversation they are authorized to access.

The endpoint must receive the requested file ID and identify the authenticated user through the existing JWT authentication context.

The `FileController` must delegate file retrieval and authorization to `FileService`.

The file must only be returned if the requesting user has permission to access the conversation associated with the attachment.

### 9.2 Request Format

**Headers:**

- `Authorization: Bearer <token>`

**Path parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `fileId` | UUID | Yes | Identifies the attachment to download |

**Example request:**

`GET /api/files/550e8400-e29b-41d4-a716-446655440000`

The frontend must send the file ID belonging to the attachment that was clicked.

The request must not contain a user ID, storage key, or physical filesystem path.

The backend must identify the authenticated user through the existing security context.

No request body is required.

### 9.3 Response Format

**Successful response:** `200 OK`

The backend must return the requested file as a binary response.

The response must contain the appropriate HTTP headers:

- `Content-Type`: The validated file's MIME type stored in the database.
- `Content-Disposition: attachment`: Instructs the browser to download the file rather than preview it.
- `Content-Length`: The size of the file in bytes.

The original filename stored in the database must be used as the suggested download filename.

**Example response headers:**

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="contract.pdf"
Content-Length: 245760
```

The backend must safely encode the original filename in the `Content-Disposition` header.

The physical file must be retrieved using its internal storage key.

The file content should be streamed to the client without unnecessarily loading the entire file into memory.

Internal storage keys and filesystem paths must never be included in the response.

### 9.4 Error Handling

The endpoint must reuse the application's existing exception and error-handling architecture.

| Status | Scenario |
|---|---|
| `400 Bad Request` | Invalid or malformed file ID |
| `401 Unauthorized` | Missing or invalid JWT authentication |
| `404 Not Found` | File does not exist, is not attached to a message, or its physical content cannot be found |
| `500 Internal Server Error` | Unexpected filesystem, database, or server failure |

**Error handling requirements:**

- Unauthorized downloads must never return file content.
- A user must not be able to download files from another customer's conversation.
- The backend must verify authorization before accessing the physical file.
- Files that are not associated with a chat message must not be downloadable.
- If the requested file cannot be found, an appropriate error response must be returned.
- Internal storage keys, physical filesystem paths, and sensitive technical details must not be exposed in error responses.
- Errors must follow the existing application's error response structure.
- Unexpected errors must be logged on the backend.
- Failed downloads must not modify or delete the stored file or its metadata.

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