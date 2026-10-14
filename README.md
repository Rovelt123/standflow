# Standflow

A stall-holder application and customer-management system for the Christmas market at Engestofte Gods. Built for the AIDA course (AI-Drevne Applikationer).

**Live:** https://standflow.roneu.dk/

## What it does
The organiser used to receive all stall-holder applications by email and lost the overview. Standflow collects it in one place: applicants register and apply online; the organiser reviews applications with statuses and internal notes, requests missing info, and sends templated emails/newsletters to segmented recipients. A public page lists only approved stall-holders.

## Tech stack
**Backend:** Java 21, Javalin (REST API), Hibernate ORM, PostgreSQL (H2 for tests), JWT + BCrypt, JUnit 5.
**Frontend:** React 19, Vite, React Router, CSS Modules.

## Architecture
React frontend -> Javalin REST API -> service layer -> Hibernate -> PostgreSQL. The frontend never talks to the database directly; role-based access (ANYONE/USER/ADMIN/OWNER) is enforced centrally; DTOs shape responses so internal fields are never exposed.

## Features
- Registration/login with GDPR consents and "remember me"
- Online stall application (with loading last year's approved application)
- Admin control panel: list and detail view, status handling, internal notes, search/filter/sort, CSV export
- Email templates with merge-fields filled from applicant data
- Newsletter with recipient segmentation
- Customer and admin messaging
- File upload (PDF and images) with content-based validation
- Public, paginated list of approved stall-holders

## Running locally
Backend: `cd backend && mvn test` runs the tests; then run the app on Javalin.
Frontend: `cd frontend && npm install && npm run dev`.
Database and SMTP are configured in the backend config; secrets and API keys stay out of Git.

## Development
Spec-driven with AI code agents guided by `AGENTS.md`, `ARCHITECTURE.md` and `CODESTANDARD.md`. All work goes through pull requests into the `Review` branch with peer review before `main`.
