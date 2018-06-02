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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.SimpleChangesBrowser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import git4idea.GitExecutionException;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CheckoutFilesFromBranchDialog extends DialogWrapper {
    private final ActionListener branchChangedListener;
    private JPanel rootPanel;
    private JComboBox gitRootComboBox;
    private JLabel currentBranchLabel;
    private JComboBox<String> otherBranchComboBox;
    private JPanel fileSelectorContainer;
    private final Project project;
    private final GitVcs vcs;
    private final MissingFilesBrowser simpleChangesBrowser;

    public CheckoutFilesFromBranchDialog(final Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot) {
        super(project, true);
        this.project = project;
        this.vcs = GitVcs.getInstance(project);
        simpleChangesBrowser = new MissingFilesBrowser(project);
        fileSelectorContainer.add(simpleChangesBrowser);
        setTitle("Checkout Files From Branch");
        GitUIUtil.setupRootChooser(project, gitRoots, defaultRoot, gitRootComboBox, currentBranchLabel);
        gitRootComboBox.addActionListener(event -> onRootChanged());
        branchChangedListener = event -> onBranchChanged();
        otherBranchComboBox.addActionListener(branchChangedListener);
        onRootChanged();
        init();
    }

    public VirtualFile getSelectedRoot() {
        return (VirtualFile) gitRootComboBox.getSelectedItem();
    }

    public String getSelectedBranch() {
        return (String) (otherBranchComboBox.getSelectedItem());
    }

    public List<String> getFilesToCheckout() {
        return this.simpleChangesBrowser.getIncludedChanges().stream()
                .map(Change::getAfterRevision)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private void onRootChanged() {
        VirtualFile root = getSelectedRoot();
        // this is done as in GitMergeDialog, would have liked to use gitRepository#getBranches, but need to exclude
        // merged branches...
        GitLineHandler handler = new GitLineHandler(project, root, GitCommand.BRANCH);
        handler.setSilent(true);
        handler.addParameters("--no-color", "-a", "--no-merged");
        try {
            String output = Git.getInstance().runCommand(handler).getOutputOrThrow();
            List<String> branches = new ArrayList<>();
            for (StringTokenizer lines = new StringTokenizer(output, "\n", false); lines.hasMoreTokens(); ) {
                branches.add(lines.nextToken().substring(2));
            }
            setBranches(branches);
        } catch (VcsException ex) {
            vcs.showErrors(Collections.singletonList(ex), "Retrieving other branches");
        }
    }

    private void setBranches(List<String> branches) {
        otherBranchComboBox.removeActionListener(branchChangedListener);
        otherBranchComboBox.removeAllItems();
        branches.forEach(b -> otherBranchComboBox.addItem(b));
        otherBranchComboBox.addActionListener(branchChangedListener);
        if (otherBranchComboBox.getItemCount() > 0) {
            otherBranchComboBox.setEnabled(true);
            otherBranchComboBox.setSelectedIndex(0);
        } else {
            otherBranchComboBox.setEnabled(false);
        }

    }

    private void onBranchChanged() {
        String selectedBranch = getSelectedBranch();
        if (selectedBranch == null) {
            simpleChangesBrowser.setChangesToDisplay(Collections.emptyList());
            return;
        }
        simpleChangesBrowser.setLoading(true);
        simpleChangesBrowser.setChangesToDisplay(Collections.emptyList());
        loadTotalDiff(selectedBranch, project, getSelectedRoot(), changes -> {
            List<Change> missingFiles = changes.stream()
                    .filter(c -> c.getFileStatus() == FileStatus.ADDED)
                    .collect(Collectors.toList());
            simpleChangesBrowser.setChangesToDisplay(missingFiles);
            simpleChangesBrowser.setLoading(false);
        });
    }

    private void loadTotalDiff(String branchName, Project project, VirtualFile root, Consumer<Collection<Change>> changesConsumer) {
        new Task.Backgroundable(project, "Searching for additional files in branch " + branchName + " ...", true) {

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    Collection<Change> changes = GitChangeUtils.getDiffWithWorkingDir(project, root, branchName, null, true);
                    ApplicationManager.getApplication().invokeLater(() -> changesConsumer.accept(changes),
                            ModalityState.stateForComponent(simpleChangesBrowser));

                } catch (VcsException e) {
                    throw new GitExecutionException("Couldn't get [git diff " + branchName + "] on repository [" + root + "]", e);
                }
            }
        }.queue();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return gitRootComboBox;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return rootPanel;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1));
        final JLabel label1 = new JLabel();
        label1.setText("Checkout files from branch:");
        rootPanel.add(label1, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        currentBranchLabel = new JLabel();
        currentBranchLabel.setText("");
        rootPanel.add(currentBranchLabel, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        gitRootComboBox = new JComboBox();
        rootPanel.add(gitRootComboBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        otherBranchComboBox = new JComboBox();
        rootPanel.add(otherBranchComboBox, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Git Root:");
        rootPanel.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Current Branch:");
        rootPanel.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Files to checkout:");
        rootPanel.add(label4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileSelectorContainer = new JPanel();
        fileSelectorContainer.setLayout(new GridBagLayout());
        rootPanel.add(fileSelectorContainer, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

    private static class MissingFilesBrowser extends SimpleChangesBrowser {
        public MissingFilesBrowser(@NotNull Project project) {
            super(project, true, false);
            setLoading(false);
        }

        public void setLoading(boolean loading) {
            getViewer().setEmptyText(
                    loading ? "Loading diff..." : "No additional files"
            );
        }

        @Override
        public void setChangesToDisplay(@NotNull Collection<? extends Change> changes) {
            super.setChangesToDisplay(changes);
        }
    }
}
