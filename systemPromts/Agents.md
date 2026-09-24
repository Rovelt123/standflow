# AGENTS.md

## Before starting work

Before modifying code:

1. Read `ARCHITECTURE.md ` for relevant architectural constraints.
2. Follow `CODESTANDARD.md`.

## Scope rules
- Only modify files necessary for the assigned issue.
- Do not perform unrelated refactoring.
- Do not change architecture without explicit approval.
- Do not change public API contracts unless required by the issue.

## Implementation workflow

1. Understand the issue and acceptance criteria.
2. Inspect existing implementation.
3. Identify affected components.
4. Implement the smallest valid change.
5. Add or update relevant tests.
6. Run required verification.
7. Verify all acceptance criteria before finishing.

## Definition of done

Work is complete only when:

- Acceptance criteria are satisfied.
- Code follows `CODESTANDARD.md`.
- Architectural rules in `ARCHITECTURE.md` are respected.
- Tests/build/lint pass.
- No unrelated files have been modified.

## Important and shared files

The following files and file categories must not be changed unless explicitly requested: 

- Build and dependency files 
- Shared environment/configuration files 
- CI/CD configuration 
- Generated files 
- Lock files 

Protected files include:

- `.gitignore`
 - `README.md` 
 - `pom.xml` 

Generated files must not be edited manually, including: 
- `backend/target/**` 