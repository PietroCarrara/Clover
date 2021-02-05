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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.Logger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;

public abstract class ThreadController
        extends Controller
        implements ThreadLayout.ThreadLayoutCallback, ImageViewerController.ImageViewerCallback,
                   SwipeRefreshLayout.OnRefreshListener, ToolbarNavigationController.ToolbarSearchCallback,
                   NfcAdapter.CreateNdefMessageCallback, ThreadSlideController.SlideChangeListener {
    protected ThreadLayout threadLayout;

    public ThreadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        navigation.handlesToolbarInset = true;

        threadLayout = (ThreadLayout) LayoutInflater.from(context).inflate(R.layout.layout_thread, null);
        threadLayout.create(this);

        view = new SwipeRefreshLayout(context) {
            @Override
            public boolean canChildScrollUp() {
                return threadLayout.canChildScrollUp();
            }
        };
        view.addView(threadLayout);
        // allows the recycler to have inertia and the drawer to be opened without the recycler taking the event away from
        // the drawer slide-to-open event
        ((SwipeRefreshLayout) view).setLegacyRequestDisallowInterceptTouchEventEnabled(true);

        ((SwipeRefreshLayout) view).setOnRefreshListener(this);

        int toolbarHeight = getToolbar().getToolbarHeight();
        ((SwipeRefreshLayout) view).setProgressViewOffset(false, toolbarHeight - dp(40), toolbarHeight + dp(64 - 40));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        threadLayout.destroy();

        EventBus.getDefault().unregister(this);
    }

    public void showSitesNotSetup() {
        threadLayout.getPresenter().showNoContent();
    }

    /*
     * Used to save instance state
     */
    public Loadable getLoadable() {
        return threadLayout.getPresenter().getLoadable();
    }

    public void handleShareAndOpenInBrowser(boolean share) {
        Loadable threadLoadable = threadLayout.getPresenter().getLoadable();
        if (threadLoadable == null) {
            return;
        }

        String link = threadLoadable.desktopUrl();

        if (share) {
            shareLink(link);
        } else {
            openLinkInBrowser(context, link);
        }
    }

    public void highlightPostNo(int postNo) {
        threadLayout.getPresenter().highlightPostNo(postNo);
    }

    @Override
    public boolean onBack() {
        return threadLayout.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Subscribe
    public void onEvent(Chan.ForegroundChangedMessage message) {
        threadLayout.getPresenter().onForegroundChanged(message.inForeground);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        onRefresh();
    }

    @Override
    public void onRefresh() {
        threadLayout.getPresenter().requestData();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Loadable loadable = getLoadable();
        String url = null;
        NdefMessage message = null;

        if (loadable != null) {
            url = loadable.desktopUrl();
        }

        if (url != null) {
            try {
                Logger.d(this, "Pushing url " + url + " to android beam");
                NdefRecord record = NdefRecord.createUri(url);
                message = new NdefMessage(new NdefRecord[]{record});
            } catch (IllegalArgumentException e) {
                Logger.e(this, "NdefMessage create error", e);
            }
        }

        return message;
    }

    @Override
    public void openReportController(final Post post) {
        navigationController.pushController(new ReportController(context, post, getLoadable()));
    }

    public void selectPostImage(PostImage postImage) {
        threadLayout.getPresenter().selectPostImage(postImage);
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, final ThumbnailView thumbnail) {
        boolean isAlreadyPresenting =
                isAlreadyPresenting((controller) -> controller instanceof ImageViewerNavigationController);

        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getBitmap() != null && !isAlreadyPresenting) {
            ImageViewerNavigationController imagerViewer = new ImageViewerNavigationController(context);
            presentController(imagerViewer, false);
            imagerViewer.showImages(images, index, loadable, this);
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(PostImage postImage) {
        return threadLayout.getThumbnail(postImage);
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        threadLayout.getPresenter().scrollToImage(postImage, true);
    }

    @Override
    public void showAlbum(List<PostImage> images, int index) {
        if (threadLayout.getPresenter().getChanThread() != null) {
            AlbumViewController albumViewController = new AlbumViewController(context);
            albumViewController.setImages(getLoadable(), images, index, navigation.title);

            if (doubleNavigationController != null) {
                doubleNavigationController.pushController(albumViewController);
            } else {
                navigationController.pushController(albumViewController);
            }
        }
    }

    @Override
    public void onShowPosts(Loadable loadable) {
    }

    @Override
    public void hideSwipeRefreshLayout() {
        ((SwipeRefreshLayout) view).setRefreshing(false);
    }

    @Override
    public Toolbar getToolbar() {
        return navigationController instanceof ToolbarNavigationController ? navigationController.getToolbar() : null;
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        threadLayout.getPresenter().onSearchVisibilityChanged(visible);
    }

    @Override
    public void onSearchEntered(String entered) {
        threadLayout.getPresenter().onSearchEntered(entered);
    }

    @Override
    public void onNavItemSet() {}

    @Override
    public void openFilterForType(FilterType type, String filterText) {
        FiltersController filtersController = new FiltersController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(filtersController);
        } else {
            navigationController.pushController(filtersController);
        }

        Filter filter = new Filter();
        filter.type = type.flag;
        filter.pattern = '/' + (filterText == null ? "" : filterText) + '/';

        filtersController.showFilterDialog(filter);
    }

    @Override
    public void onSlideChanged() {
        threadLayout.gainedFocus();
    }

    @Override
    public boolean threadBackPressed() {
        return false;
    }

    @Override
    public boolean isViewingCatalog() {
        if (doubleNavigationController != null) {
            return doubleNavigationController.isViewingCatalog();
        }
        return false;
    }
}
