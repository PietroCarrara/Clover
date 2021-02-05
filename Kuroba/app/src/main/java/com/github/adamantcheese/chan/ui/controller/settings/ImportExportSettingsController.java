/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.presenter.ImportExportSettingsPresenter;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.LoadingViewController;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.callback.FileChooserCallback;
import com.github.k1rakishou.fsaf.callback.FileCreateCallback;
import com.github.k1rakishou.fsaf.file.ExternalFile;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImportExportSettingsController
        extends SettingsController
        implements ImportExportSettingsPresenter.ImportExportSettingsCallbacks {
    public static final String EXPORT_FILE_NAME = getApplicationLabel() + "_exported_settings.json";

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;

    private ImportExportSettingsPresenter presenter;

    @Nullable
    private OnExportSuccessCallbacks callbacks;

    private final LoadingViewController loadingViewController;

    public ImportExportSettingsController(Context context, @NonNull OnExportSuccessCallbacks callbacks) {
        super(context);

        this.callbacks = callbacks;
        this.loadingViewController = new LoadingViewController(context, true);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_import_export);

        presenter = new ImportExportSettingsPresenter(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (presenter != null) {
            presenter.onDestroy();
        }

        callbacks = null;
    }

    @Override
    protected void populatePreferences() {
        // Import/export settings group
        {
            SettingsGroup group = new SettingsGroup(getString(R.string.import_or_export_settings));

            group.add(new LinkSettingView(
                    this,
                    getString(R.string.export_settings),
                    getString(R.string.export_settings_to_a_file),
                    v -> onExportClicked()
            ));

            group.add(new LinkSettingView(
                    this,
                    getString(R.string.import_settings),
                    getString(R.string.import_settings_from_a_file),
                    v -> onImportClicked()
            ));

            groups.add(group);
        }
    }

    private void onExportClicked() {
        boolean savedFilesLocationIsSAFBacked = ChanSettings.saveLocation.isSafDirActive();

        if (savedFilesLocationIsSAFBacked) {
            showDirectoriesWillBeResetToDefaultDialog(savedFilesLocationIsSAFBacked);
            return;
        }

        showCreateNewOrOverwriteDialog();
    }

    private void showDirectoriesWillBeResetToDefaultDialog(boolean savedFilesLocationIsSAFBacked) {
        String savedFilesString =
                savedFilesLocationIsSAFBacked ? getString(R.string.import_or_export_warning_saved_files_base_dir) : "";

        String messagePartOne =
                getString(R.string.import_or_export_warning_super_long_message_part_one, savedFilesString);

        getDefaultAlertBuilder(context).setTitle(getString(R.string.import_or_export_warning))
                .setMessage(messagePartOne)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                    showCreateNewOrOverwriteDialog();
                })
                .create()
                .show();
    }

    /**
     * SAF is kinda horrible so it cannot be used to overwrite a file that already exist on the disk
     * (or at some network location). When trying to do so, a new file with appended "(1)" at the
     * end will appear. That's why there are two methods (one for overwriting an existing file and
     * the other one for creating a new file) instead of one that does everything.
     */
    private void showCreateNewOrOverwriteDialog() {
        int positiveButtonId = R.string.import_or_export_dialog_positive_button_text;
        int negativeButtonId = R.string.import_or_export_dialog_negative_button_text;

        getDefaultAlertBuilder(context).setTitle(R.string.import_or_export_dialog_title)
                .setPositiveButton(positiveButtonId, (dialog, which) -> overwriteExisting())
                .setNegativeButton(negativeButtonId, (dialog, which) -> createNew())
                .create()
                .show();
    }

    /**
     * Opens an existing file (any file) for overwriting with the settings.
     */
    private void overwriteExisting() {
        fileChooser.openChooseFileDialog(new FileChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                onFileChosen(uri, false);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showToast(context, reason, Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Creates a new file with the default name (that can be changed in the file chooser) with the
     * settings. Cannot be used for overwriting an old settings file (when trying to do so a new file
     * with appended "(1)" at the end will appear, e.g. "test (1).txt")
     */
    private void createNew() {
        fileChooser.openCreateFileDialog(EXPORT_FILE_NAME, new FileCreateCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                onFileChosen(uri, true);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showToast(context, reason, Toast.LENGTH_LONG);
            }
        });
    }

    private void onFileChosen(Uri uri, boolean isNewFile) {
        // We use SAF here by default because settings importing/exporting does not depend on the
        // Kuroba default directory location. There is just no need to use old java files.
        ExternalFile externalFile = fileManager.fromUri(uri);
        if (externalFile == null) {
            String message = "onFileChosen() fileManager.fromUri() returned null, uri = " + uri;

            Logger.d(this, message);
            showToast(context, message, Toast.LENGTH_LONG);
            return;
        }

        navigationController.presentController(loadingViewController);
        presenter.doExport(externalFile, isNewFile);
    }

    private void onImportClicked() {
        fileChooser.openChooseFileDialog(new FileChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                ExternalFile externalFile = fileManager.fromUri(uri);
                if (externalFile == null) {
                    String message = "onImportClicked() fileManager.fromUri() returned null, uri = " + uri;

                    Logger.d(ImportExportSettingsController.this, message);
                    showToast(context, message, Toast.LENGTH_LONG);
                    return;
                }

                navigationController.presentController(loadingViewController);
                presenter.doImport(externalFile);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                showToast(context, reason, Toast.LENGTH_LONG);
            }
        });
    }

    @Override
    public void onSuccess(ImportExportRepository.ImportExport importExport) {
        // called on background thread
        if (context instanceof StartActivity) {
            BackgroundUtils.runOnMainThread(() -> {
                if (importExport == ImportExportRepository.ImportExport.Import) {
                    ((StartActivity) context).restartApp();
                } else {
                    loadingViewController.stopPresenting();
                    showToast(context, R.string.successfully_exported_text, Toast.LENGTH_LONG);

                    if (callbacks != null) {
                        callbacks.finish();
                    }
                }
            });
        }
    }

    @Override
    public void onError(String message) {
        BackgroundUtils.runOnMainThread(() -> {
            loadingViewController.stopPresenting();
            showToast(context, message, Toast.LENGTH_LONG);
        });
    }

    public interface OnExportSuccessCallbacks {
        void finish();
    }
}
