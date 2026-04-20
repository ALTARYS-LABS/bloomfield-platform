▎ PR #17 (feat/staging-environment → develop) has merge conflicts and a failing CI check. The root
cause is that main and develop diverged at f2235fc — the same changes were committed directly to both
branches with different hashes, violating Gitflow. Check memory for project_pr17_fix.md which has the
full diagnosis.

▎ Execute the fix:
▎ 1. Merge origin/main into develop, resolve conflicts (both sides have equivalent changes), push to
remote
▎ 2. Rebase feat/staging-environment on the reconciled develop, force-push with lease
▎ 3. Verify PR #17 is clean and CI passes

▎ Ask me before pushing to any remote shared branch.