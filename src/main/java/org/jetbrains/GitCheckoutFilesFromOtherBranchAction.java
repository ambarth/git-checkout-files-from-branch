/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.actions.GitRepositoryAction;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user checkout files from another branch that do not exist on the current branch, which is achieved by a
 * simple `git checkout some-branch -- /path/to/some/file`.
 */
public class GitCheckoutFilesFromOtherBranchAction extends GitRepositoryAction {

    @NotNull
    @Override
    protected String getActionName() {
        return "Checkout files from branch";
    }

    @Override
    protected void perform(@NotNull Project project,
                           @NotNull List<VirtualFile> gitRoots,
                           @NotNull VirtualFile defaultRoot) {
        CheckoutFilesFromBranchDialog d = new CheckoutFilesFromBranchDialog(project, gitRoots, defaultRoot);
        if (!d.showAndGet()) {
            return;
        }

        VirtualFile selectedRoot = d.getSelectedRoot();
        String selectedBranch = d.getSelectedBranch();
        List<String> filesToCheckout = d.getFilesToCheckout();
        if (filesToCheckout.isEmpty()) {
            return;
        }
        new Task.Backgroundable(project, "Checking Out Files...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try (AccessToken ignored = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
                    GitCommandResult result = Git.getInstance().runCommand(
                            () -> createCheckoutFilesCommand(project, selectedRoot, selectedBranch, filesToCheckout));
                    if (!result.success()) {
                        VcsNotifier.getInstance(project).notifyError("Checking Out Files...",
                                result.getErrorOutputAsHtmlString());
                    }
                }
            }

            @Override
            public void onFinished() {
                VfsUtil.markDirtyAndRefresh(false, true, false, selectedRoot);
            }
        }.queue();
    }

    private GitLineHandler createCheckoutFilesCommand(Project project, VirtualFile selectedRoot, String selectedBranch,
                                                      List<String> filesToCheckout) {
        List<String> params = new ArrayList<>();
        params.add(selectedBranch);
        params.add("--");
        params.addAll(filesToCheckout);
        GitLineHandler handler = new GitLineHandler(project, selectedRoot, GitCommand.CHECKOUT);
        handler.addParameters(params);
        return handler;
    }

}