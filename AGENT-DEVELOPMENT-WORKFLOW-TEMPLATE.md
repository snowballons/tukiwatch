# Agentic Development Workflow Template (Multica + Git Worktree Integration)

This document defines the standardized, step-by-step agentic development workflow for projects managed via Multica and local git repositories. It bridges the gap between automated remote agent task execution and rigorous local engineering review, testing, and merging.

---

## 1. Core Architecture & Execution Model

Understanding where execution happens ensures safety, correctness, and clean branch management.

| Component | Responsibilities | Data Boundaries |
| :--- | :--- | :--- |
| **Multica Server** | Task queue, issues, comments, statuses, agent configs, skills, run telemetry. | Metadata, project specs, task coordination. |
| **Local Machine (Daemon)** | Invokes AI coding runtimes (Claude Code, etc.), executes shell commands, performs edits. | Local file system, working directories, codebases. |

### Execution Boundaries
- **The Multica server** does not execute shell commands on behalf of local tools and does not automatically upload your entire working directory.
- **The Local Daemon** claims tasks from the queue, runs the agent locally against the target codebase, and pushes resulting branches to GitHub (`origin`).

---

## 2. Project Resource Modes

When configuring Multica projects, choose the appropriate resource binding:

1. **GitHub Repository Resource**:
   - Runtime clones a fresh working directory per task or uses managed worktrees.
   - Isolated execution environment.
2. **Local Directory Resource (Recommended for active dev)**:
   - Targets the original directory on your local machine.
   - **Worktree Mode (`execution_mode: worktree`)**:
     - Each task runs in an isolated git worktree created within Multica's workspace directory (`~/multica_workspaces_.../`).
     - The agent's changes are packaged and pushed as a remote branch: `agent/<agent-name>/<task-hash>`.
     - **Important**: Multica never automatically merges agent branches. You are responsible for reviewing, testing, and merging.

---

## 3. Step-by-Step Agentic Workflow

### Phase 1: Task Definition & Issue Preparation
1. **Define the Issue**: Create a clear issue or task in Multica with precise requirements, acceptance criteria, and constraints.
2. **Attach Context**: Link relevant specs, architecture decisions (ADRs), and files.
3. **Dispatch**: Assign the task to the designated AI agent (e.g., Mika, Task Agent) in Multica.

### Phase 2: Agent Execution & Remote Handoff
1. **Daemon Execution**: The local Multica daemon picks up the queued task and runs the AI coding tool inside the designated workspace/worktree.
2. **Autonomous Coding**: The agent implements the feature/fix, runs tool-level checks, and commits changes.
3. **Branch Push**: Upon completion, the agent/daemon pushes the branch to GitHub:
   ```bash
   origin/agent/<agent-name>/<task-hash>
   ```
4. **Notification**: Multica marks the task as completed and reports the pushed branch in the issue timeline.

### Phase 3: Local Review & Worktree Isolation (Crucial Step)
Never test agent changes directly in your main active development branch (`main`). Isolate the review into a dedicated local git worktree.

1. **Fetch Remote Agent Branches**:
   ```bash
   git fetch origin
   git branch -r | grep agent/
   ```
2. **Create an Isolated Review Worktree**:
   ```bash
   # Create a local worktree for the specific issue/task
   git worktree add ~/review-<issue-id> origin/agent/<agent-name>/<task-hash>
   cd ~/review-<issue-id>
   ```

### Phase 4: Quality Gates & Testing
Run all project quality gates and manual smoke tests inside the isolated review worktree:

1. **Install Dependencies** (if needed):
   ```bash
   bun install # or npm install / pip install / uv sync
   ```
2. **Static Analysis & Linting**:
   ```bash
   bunx @biomejs/biome ci . # or ruff check . / eslint
   ```
3. **Type Checking**:
   ```bash
   bun run type-check # or tsc --noEmit / pyright
   ```
4. **Test Suite**:
   ```bash
   bun test # or pytest / cargo test
   ```
5. **Manual Smoke Test**:
   - Launch the application or service in development mode.
   - Verify the specific acceptance criteria for the issue.

### Phase 5: Integration & Merging
If quality gates pass and behavior is verified:

1. **Return to Main Repository**:
   ```bash
   cd ~/Projects/tukiwatch-org/tukiwatch-mobile/tukiwatch
   git checkout main
   git pull --rebase origin main
   ```
2. **Merge the Agent Branch (No-Fast-Forward)**:
   ```bash
   git merge --no-ff review-<issue-id> -m "Merge issue #<issue-id>: <task-title>"
   ```
3. **Final Validation on Main**:
   - Run a quick build/test check on `main`.
4. **Push to Origin**:
   ```bash
   git push origin main
   ```

### Phase 6: Cleanup
Clean up local review worktrees and remote branches to keep the environment pristine:

1. **Remove Local Worktree**:
   ```bash
   git worktree remove ~/review-<issue-id>
   ```
2. **Delete Local Review Branch (if any)**:
   ```bash
   git branch -d review-<issue-id>
   ```
3. **Delete Remote Agent Branch (Optional)**:
   ```bash
   git push origin --delete agent/<agent-name>/<task-hash>
   ```

---

## 4. Best Practices & Safety Guidelines

- **Never Skip Isolation**: Always use `git worktree add` to review agent branches. Direct merging without local testing risks regressions.
- **Preserve Commit History**: Use `git merge --no-ff` to maintain a clear audit trail of agent-contributed work in the git graph.
- **Strict Quality Gates**: Do not merge PRs or branches that fail lint, type-check, or unit tests.
- **Credential Safety**: Ensure local `.env` files and secrets are never committed or exposed during agent runs.
