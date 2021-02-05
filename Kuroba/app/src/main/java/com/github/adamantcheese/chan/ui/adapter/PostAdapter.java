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
package com.github.adamantcheese.chan.ui.adapter;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_POST_STUB;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.CellType.TYPE_STATUS;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    enum CellType {
        TYPE_POST,
        TYPE_STATUS,
        TYPE_POST_STUB
    }

    private final PostAdapterCallback postAdapterCallback;
    private final PostCellInterface.PostCellCallback postCellCallback;
    private final RecyclerView recyclerView;

    private final ThreadStatusCell.Callback statusCellCallback;
    private final List<Post> displayList = new ArrayList<>();

    private Loadable loadable = null;
    private String error = null;
    private String highlightedId;
    private int highlightedNo = -1;
    private String highlightedTripcode;
    private String searchQuery;
    private int lastSeenIndicatorPosition = Integer.MIN_VALUE;

    private ChanSettings.PostViewMode postViewMode;
    private boolean compact = false;
    private final Theme theme;
    private final RecyclerView.ItemDecoration divider;

    public PostAdapter(
            RecyclerView recyclerView,
            PostAdapterCallback postAdapterCallback,
            PostCellInterface.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback,
            Theme theme
    ) {
        this.recyclerView = recyclerView;
        this.postAdapterCallback = postAdapterCallback;
        this.postCellCallback = postCellCallback;
        this.statusCellCallback = statusCellCallback;
        this.theme = theme;
        setHasStableIds(true);

        divider = RecyclerUtils.getBottomDividerDecoration(recyclerView.getContext());
        final ShapeDrawable lastSeen = new ShapeDrawable();
        lastSeen.setTint(getAttrColor(recyclerView.getContext(), R.attr.colorAccent));

        // Last seen decoration
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDrawOver(
                    @NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state
            ) {
                super.onDrawOver(c, parent, state);
                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    if (parent.getChildAdapterPosition(child) == lastSeenIndicatorPosition) {
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                        int dividerTop = child.getBottom() + params.bottomMargin;
                        int dividerBottom = dividerTop + dp(4);

                        lastSeen.setBounds(0, dividerTop, parent.getWidth(), dividerBottom);
                        lastSeen.draw(c);
                    }
                }
            }

            @Override
            public void getItemOffsets(
                    @NonNull Rect outRect,
                    @NonNull View view,
                    @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state
            ) {
                super.getItemOffsets(outRect, view, parent, state);
                if (parent.getChildAdapterPosition(view) == lastSeenIndicatorPosition) {
                    outRect.top = dp(4);
                }
            }
        });
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (CellType.values()[viewType]) {
            case TYPE_POST:
                int layout = 0;
                switch (getPostViewMode()) {
                    case LIST:
                        layout = R.layout.cell_post;
                        break;
                    case CARD:
                        layout = R.layout.cell_post_card;
                        break;
                }

                PostCellInterface postCell =
                        (PostCellInterface) LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
                return new PostViewHolder(postCell);
            case TYPE_POST_STUB:
                PostCellInterface postCellStub = (PostCellInterface) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_post_stub, parent, false);
                return new PostViewHolder(postCellStub);
            case TYPE_STATUS:
                ThreadStatusCell statusCell = (ThreadStatusCell) LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cell_thread_status, parent, false);
                StatusViewHolder statusViewHolder = new StatusViewHolder(statusCell);
                statusCell.setCallback(statusCellCallback);
                statusCell.setError(error);
                return statusViewHolder;
            default:
                throw new IllegalStateException("Unknown view holder");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);
        switch (CellType.values()[itemViewType]) {
            case TYPE_POST:
            case TYPE_POST_STUB:
                if (loadable == null) {
                    throw new IllegalStateException("Loadable cannot be null");
                }

                PostViewHolder postViewHolder = (PostViewHolder) holder;
                Post post = displayList.get(position);
                ((PostCellInterface) postViewHolder.itemView).setPost(
                        loadable,
                        post,
                        postCellCallback,
                        isInPopup(),
                        shouldHighlight(post),
                        getMarkedNo(),
                        getPostViewMode(),
                        isCompact(),
                        searchQuery,
                        theme
                );

                if (itemViewType == TYPE_POST_STUB.ordinal() && postAdapterCallback != null) {
                    holder.itemView.setOnClickListener(v -> postAdapterCallback.onUnhidePostClick(post));
                }
                break;
            case TYPE_STATUS:
                ((ThreadStatusCell) holder.itemView).update();
                break;
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        //this is a hack to make sure text is selectable
        super.onViewAttachedToWindow(holder);
        try {
            holder.itemView.findViewById(R.id.comment).setEnabled(false);
            holder.itemView.findViewById(R.id.comment).setEnabled(true);
        } catch (Exception ignored) {}
    }

    public boolean isInPopup() {
        return false;
    }

    public boolean shouldHighlight(Post post) {
        return post.id.equals(highlightedId) || post.no == highlightedNo || post.tripcode.equals(highlightedTripcode);
    }

    public int getMarkedNo() {
        return -1;
    }

    public ChanSettings.PostViewMode getPostViewMode() {
        return postViewMode;
    }

    @Override
    public int getItemCount() {
        return displayList.size() + (showStatusView() ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (showStatusView() && position == getItemCount() - 1) {
            return TYPE_STATUS.ordinal();
        } else {
            Post post = displayList.get(position);
            if (post.filterStub) {
                return TYPE_POST_STUB.ordinal();
            } else {
                return TYPE_POST.ordinal();
            }
        }
    }

    @Override
    public long getItemId(int position) {
        int itemViewType = getItemViewType(position);
        if (itemViewType == TYPE_STATUS.ordinal()) {
            return -2;
        } else {
            return displayList.get(position).no;
        }
    }

    public void setThread(ChanThread thread, PostsFilter filter) {
        BackgroundUtils.ensureMainThread();

        this.loadable = thread.getLoadable();
        boolean queryChanged =
                this.searchQuery != null && filter != null && !this.searchQuery.equals(filter.getQuery());
        this.searchQuery = filter == null ? null : filter.getQuery();

        showError(null);

        List<Post> newList = filter == null ? thread.getPosts() : filter.apply(thread);

        lastSeenIndicatorPosition = Integer.MIN_VALUE;
        // Do not process the last post, the indicator does not have to appear at the bottom
        for (int i = 0; i < newList.size() - 1; i++) {
            if (newList.get(i).no == loadable.lastViewed) {
                lastSeenIndicatorPosition = i;
                break;
            }
        }

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            // +1 for status cells
            @Override
            public int getOldListSize() {
                return displayList.size() + (showStatusView() ? 1 : 0);
            }

            @Override
            public int getNewListSize() {
                return newList.size() + (showStatusView() ? 1 : 0);
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                // if the query's changed, invalidate all items
                // if the status view is shown and the oldposition/newposition matches the list size, invalidate that as well (index is status cell)
                if (queryChanged || (showStatusView() && oldItemPosition == displayList.size()) || (showStatusView()
                        && newItemPosition == newList.size())) {
                    return false;
                }
                return displayList.get(oldItemPosition).no == newList.get(newItemPosition).no;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                // uses Post.equals() to compare
                return displayList.get(oldItemPosition).equals(newList.get(newItemPosition));
            }
        });

        displayList.clear();
        displayList.addAll(newList);

        result.dispatchUpdatesTo(this); // better than notifyDataSetChanged for small UI updates, but can also act as a full refresh if needed
    }

    public void setLastSeenIndicatorPosition(int position) {
        lastSeenIndicatorPosition = position;
        notifyDataSetChanged();
    }

    public List<Post> getDisplayList() {
        return displayList;
    }

    public void cleanup() {
        highlightedId = null;
        highlightedNo = -1;
        highlightedTripcode = null;
        lastSeenIndicatorPosition = Integer.MIN_VALUE;
        error = null;
    }

    public void showError(String error) {
        this.error = error;
        if (showStatusView()) {
            final int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                if (child instanceof ThreadStatusCell) {
                    ((ThreadStatusCell) child).setError(error);
                }
            }
        }
    }

    public void highlightPostId(String id) {
        highlightedId = id;
        highlightedNo = -1;
        highlightedTripcode = null;
        notifyDataSetChanged();
    }

    public void highlightPostTripcode(String tripcode) {
        highlightedId = null;
        highlightedNo = -1;
        highlightedTripcode = tripcode;
        notifyDataSetChanged();
    }

    public void highlightPostNo(int no) {
        highlightedId = null;
        highlightedNo = no;
        highlightedTripcode = null;
        notifyDataSetChanged();
    }

    public void setPostViewMode(ChanSettings.PostViewMode postViewMode) {
        this.postViewMode = postViewMode;

        if (postViewMode == ChanSettings.PostViewMode.LIST) {
            recyclerView.addItemDecoration(divider);
        } else {
            recyclerView.removeItemDecoration(divider);
        }
    }

    public void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
        }
    }

    public boolean isCompact() {
        return compact;
    }

    public boolean showStatusView() {
        if (postAdapterCallback == null) return false;
        // the loadable can be null while this adapter is used between cleanup and the removal
        // of the recyclerview from the view hierarchy, although it's rare.
        return loadable != null && loadable.isThreadMode();
    }

    public static class PostViewHolder
            extends RecyclerView.ViewHolder {
        public PostViewHolder(PostCellInterface postView) {
            super((View) postView);
        }
    }

    public static class StatusViewHolder
            extends RecyclerView.ViewHolder {
        public StatusViewHolder(ThreadStatusCell threadStatusCell) {
            super(threadStatusCell);
        }
    }

    public interface PostAdapterCallback {
        void onUnhidePostClick(Post post);
    }
}
