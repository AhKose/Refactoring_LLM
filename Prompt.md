# Experimental Prompting Protocol

This document outlines the **3-Stage Chain-of-Thought Prompting Strategy** utilized in this study. The experiment follows a strict iterative process to ensure the Large Language Model (LLM) understands the architectural context before attempting any code modifications.

The interaction is designed as a multi-turn conversation with a specific "Stop & Think" mechanism to prevent premature code generation.

## 📌 Dynamic Variables
The prompts below contain placeholders represented in brackets. These were dynamically replaced for each microservice during the experiment:
* `[service_name]`: The name of the specific microservice being analyzed (e.g., *AuthService*).
* `[context_files]`: The list of read-only files provided for context (e.g., *UserEntity.java, DatabaseConfig.java*).
* `[path]`: The file path of the source code being modified.

---

## 🟢 Phase 1: Contextual Analysis & Planning (Turn 1)

**Objective:** To establish the role, load the architectural context, and identify refactoring opportunities without generating code.

**Prompt:**
```text
INSTRUCTION PHASE (TURN 1):
ROLE: Senior Software Architect & Refactoring Specialist
CONTEXT: You are provided with the context of a microservice project.

1. Read-only Context: pom.xml and [context_files]. Read these only to understand dependencies and contracts; do NOT modify them.
2. Target Source Code: Source code files for the [service_name] Service. You are authorized to inspect and propose modifications.

MISSION (Audit & Decision): Inspect the target source code specifically for the following Refactoring Triggers:
· Long Functions and Classes: Entities that have grown too large, hindering readability.
· Code Duplication: Repeated logic that should be centralized.
· High Internal Coupling: Classes that are too tightly dependent on each other.
· Complex Loops & Conditions: Deeply nested structures or complicated logic flow.
· Multi-Responsibility Methods/Classes: Methods or classes that handle too many tasks (avoiding multi-responsibility).

REFACTORING INSTRUCTIONS (Strictly In-File): Clean up the code without creating new .java files.
· Extract Method: Break long functions into smaller, private helper methods within the same class.
· In-File Class Splitting: If a class must be split, create non-public (package-private) classes at the end of the same .java file (outside the main public class block).
· Encapsulation & Hiding: Mark all extracted methods and logic as private unless they are required for the external API. Only keep existing public signatures as public to maintain stability.

HARD GUARDRAILS:
· NO NEW FILES: You are strictly forbidden from creating new file paths.
· API Stability: Do NOT change @Path, HTTP methods, or public method signatures.
· Structural Integrity: Do NOT modify interfaces/entities or pom.xml.

OUTPUT (DO NOT WRITE CODE YET): 
A) List files requiring changes under the heading: "Refactoring Required." 
B) Format: ### FILE: <path> 
C) Justification: One sentence per file identifying the trigger (e.g., Multi-Responsibility Method) and the intended internal technique.

STOP. Only provide the list and analysis. Do not generate code yet.
```

## 🟡 Phase 2: Implementation (Turn 2)
**Objective:** To execute the refactoring plan strictly within the boundaries of the existing files.
**Prompt:**
```text
Approved. Implement these refactorings that are listed within the original file boundaries.

Follow this STRICT format for every change:
1. File Header: ### FILE: <path>
2. Change Description: Explain the internal refactoring (e.g., "Refactoring a Multi-Responsibility method into private helper methods to improve internal cohesion").
3. Location: Specify the Approximate Line Number in the original file.
4. Comparison Blocks:
   · ORIGINAL CODE: Provide the existing block.
   · REFACTORED CODE: Provide the new, updated block (including any private methods or package-private classes added to the file).

RULES:
· Do NOT provide the full file.
· Ensure all new logic is private to reduce coupling.
· Maintain absolute API stability.
```

## 🔴 Phase 3: Verification & Documentation (Turn 3)
**Objective:** To perform a self-audit of the generated code and generate a technical summary of the improvements.
**Prompt:**
```text
Review your provided code snippets.

1. SELF-AUDIT: Check for missing imports or syntax errors. Ensure ZERO new files were created and all extracted logic is properly encapsulated (private).
2. JUSTIFICATION: Explain technically why this internal refactoring makes the code "better" and reduces internal coupling without breaking the API.
3. SUMMARY: Provide a 5-point summary of the improvements made to maintainability and readability.
```
