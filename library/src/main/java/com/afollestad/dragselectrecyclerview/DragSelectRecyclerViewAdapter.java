package com.afollestad.dragselectrecyclerview;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AndroidRuntimeException;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class DragSelectRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    public interface SelectionListener {
        void onDragSelectionChanged(int count);
    }

    private ArrayList<Integer> mSelectedIndices;
    private SelectionListener mSelectionListener;
    private int mLastCount = -1;
    private int mMaxSelectionCount = -1;

    private void fireSelectionListener() {
        if (mLastCount == mSelectedIndices.size())
            return;
        mLastCount = mSelectedIndices.size();
        if (mSelectionListener != null)
            mSelectionListener.onDragSelectionChanged(mLastCount);
    }

    protected DragSelectRecyclerViewAdapter() {
        mSelectedIndices = new ArrayList<>();
    }

    public void setMaxSelectionCount(int maxSelectionCount) {
        this.mMaxSelectionCount = maxSelectionCount;
    }

    public void setSelectionListener(SelectionListener selectionListener) {
        this.mSelectionListener = selectionListener;
    }

    public void saveInstanceState(Bundle out) {
        saveInstanceState("selected_indices", out);
    }

    public void saveInstanceState(String key, Bundle out) {
        out.putSerializable(key, mSelectedIndices);
    }

    public void restoreInstanceState(Bundle in) {
        restoreInstanceState("selected_indices", in);
    }

    public void restoreInstanceState(String key, Bundle in) {
        if (in != null && in.containsKey(key)) {
            //noinspection unchecked
            mSelectedIndices = (ArrayList<Integer>) in.getSerializable(key);
            if (mSelectedIndices == null) mSelectedIndices = new ArrayList<>();
            else fireSelectionListener();
        }
    }

    public final void setSelected(int sectionedIndex, boolean selected) {
        int index = sectionedPositionToPosition(sectionedIndex);
        if (!isIndexSelectable(sectionedIndex) || isSectionHeaderPosition(sectionedIndex))
            selected = false;
        if (selected) {
            if (!mSelectedIndices.contains(index) &&
                    (mMaxSelectionCount == -1 ||
                            mSelectedIndices.size() < mMaxSelectionCount)) {
                mSelectedIndices.add(index);
                notifyItemChanged(sectionedIndex);
            }
        } else if (mSelectedIndices.contains(index)) {
            mSelectedIndices.remove((Integer) index);
            notifyItemChanged(sectionedIndex);
        }
        fireSelectionListener();
    }

    public final boolean toggleSelected(int sectionedIndex) {
        boolean selectedNow = false;
        int index = sectionedPositionToPosition(sectionedIndex);
        if (isIndexSelectable(sectionedIndex) || isSectionHeaderPosition(sectionedIndex)) {
            if (mSelectedIndices.contains(index)) {
                mSelectedIndices.remove((Integer) index);
            } else if (mMaxSelectionCount == -1 ||
                    mSelectedIndices.size() < mMaxSelectionCount) {
                mSelectedIndices.add(index);
                selectedNow = true;
            }
            notifyItemChanged(sectionedIndex);
        }
        fireSelectionListener();
        return selectedNow;
    }

    protected boolean isIndexSelectable(int index) {
        return true;
    }

    public final void selectRange(int from, int to, int min, int max) {
        if (from == to) {
            // Finger is back on the initial item, unselect everything else
            for (int i = min; i <= max; i++) {
                if (i == from) continue;
                setSelected(i, false);
            }
            fireSelectionListener();
            return;
        }

        if (to < from) {
            // When selecting from one to previous items
            for (int i = to; i <= from; i++)
                setSelected(i, true);
            if (min > -1 && min < to) {
                // Unselect items that were selected during this drag but no longer are
                for (int i = min; i < to; i++) {
                    if (i == from) continue;
                    setSelected(i, false);
                }
            }
            if (max > -1) {
                for (int i = from + 1; i <= max; i++)
                    setSelected(i, false);
            }
        } else {
            // When selecting from one to next items
            for (int i = from; i <= to; i++)
                setSelected(i, true);
            if (max > -1 && max > to) {
                // Unselect items that were selected during this drag but no longer are
                for (int i = to + 1; i <= max; i++) {
                    if (i == from) continue;
                    setSelected(i, false);
                }
            }
            if (min > -1) {
                for (int i = min; i < from; i++)
                    setSelected(i, false);
            }
        }
        fireSelectionListener();
    }

    public final void clearSelected() {
        mSelectedIndices.clear();
        notifyDataSetChanged();
        fireSelectionListener();
    }

    public final int getSelectedCount() {
        return mSelectedIndices.size();
    }

    public final Integer[] getSelectedIndices() {
        return mSelectedIndices.toArray(new Integer[mSelectedIndices.size()]);
    }

    public final boolean isIndexSelected(int index) {
        return mSelectedIndices.contains(index);
    }


    /*
        Sectioned Grid
        @author Claudemir Todo Bom http://todobom.com
        Based on code by Gabrielle Mariotti on https://gist.github.com/gabrielemariotti/ad6672902464ee2392d0
     */

    private SparseArray<Section> mSections = new SparseArray<>();
    Context mContext;
    int mSectionResourceId;
    int mTextResourceId;
    RecyclerView mRecyclerView;

    protected DragSelectRecyclerViewAdapter( Context context, int sectionResourceId, int textResourceId,
                                             RecyclerView recyclerView ) {

        mContext = context;
        mSelectedIndices = new ArrayList<>();
        mRecyclerView = recyclerView;
        mSectionResourceId = sectionResourceId;
        mTextResourceId = textResourceId;


        final GridLayoutManager layoutManager = (GridLayoutManager)(mRecyclerView.getLayoutManager());

        if (layoutManager == null) {
            throw new AndroidRuntimeException("GridLayoutManager should be defined before the " +
                                                "construction of the adapter");
        }

        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (isSectionHeaderPosition(position))? layoutManager.getSpanCount() : 1 ;
            }
        });
    }

    public static class SectionViewHolder extends RecyclerView.ViewHolder {

        public TextView title;

        public SectionViewHolder(View view,int mTextResourceid) {
            super(view);
            title = (TextView) view.findViewById(mTextResourceid);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int typeView) {
        SectionViewHolder sectionViewHolder = createSectionViewHolder(parent, typeView);
        if (sectionViewHolder != null) {
            return (VH) sectionViewHolder;
        }else{
            return (VH) onCreateItemViewHolder(parent, typeView);
        }
    }

    public SectionViewHolder createSectionViewHolder(ViewGroup parent, int typeView) {
        if (typeView == mSectionResourceId) {
            final View view = LayoutInflater.from(mContext).inflate(mSectionResourceId, parent, false);
            return new SectionViewHolder(view,mTextResourceId);
        }else{
            return null;
        }
    }

    /**
     * Return the normal Item View Holder for sectioned GridView
     *
     * This method issues an Exception, it MUST be overridden on
     * any class that doesn't override onCreateViewHolder, as the
     * original implementation calls this method.
     * @param parent
     * @param typeView
     * @return
     */
    public RecyclerView.ViewHolder onCreateItemViewHolder(ViewGroup parent, int typeView) {
        throw new AndroidRuntimeException("onCreateItemViewHolder must be overridden when " +
                "onCreateViewHolder is not overridden");
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        holder.itemView.setTag(holder);
        if (isSectionHeaderPosition(position)) {
            ((SectionViewHolder)holder).title.setText(mSections.get(position).title);
        }else{
            onBindItemViewHolder(holder, sectionedPositionToPosition(position));
        }
    }

    /**
     * executed when binding a normal item (not section)
     *
     * this is a placeholder, it does nothing and MUST
     * be overridden on any class that works with
     * section headers
     * @param holder
     * @param position
     */
    public void onBindItemViewHolder(VH holder, int position) {
        return;
    }

    @Override
    public int getItemViewType(int position) {
        return isSectionHeaderPosition(position)
                ? mSectionResourceId
                : getNormalItemViewType(position) ;
    }

    public static class Section {
        int firstPosition;
        int sectionedPosition;
        CharSequence title;

        public Section(int firstPosition, CharSequence title) {
            this.firstPosition = firstPosition;
            this.title = title;
        }

        public CharSequence getTitle() {
            return title;
        }
    }

    public void setSections(Section[] sections) {
        mSections.clear();

        Arrays.sort(sections, new Comparator<Section>() {
            @Override
            public int compare(Section o, Section o1) {
                return (o.firstPosition == o1.firstPosition)
                        ? 0
                        : ((o.firstPosition < o1.firstPosition) ? -1 : 1);
            }
        });

        int offset = 0; // offset positions for the headers we're adding
        for (Section section : sections) {
            section.sectionedPosition = section.firstPosition + offset;
            mSections.append(section.sectionedPosition, section);
            ++offset;
        }

        notifyDataSetChanged();
    }


    /**
     * Get number total number of defined sections
     *
     * @return
     */
    public int getSectionsSize() {
        return mSections.size();
    }

    public int positionToSectionedPosition(int position) {
        int offset = 0;
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.valueAt(i).firstPosition > position) {
                break;
            }
            ++offset;
        }
        return position + offset;
    }

    public int sectionedPositionToPosition(int sectionedPosition) {
        if (isSectionHeaderPosition(sectionedPosition)) {
            return RecyclerView.NO_POSITION;
        }

        int offset = 0;
        for (int i = 0; i < mSections.size(); i++) {
            if (mSections.valueAt(i).sectionedPosition > sectionedPosition) {
                break;
            }
            --offset;
        }
        return sectionedPosition + offset;
    }

    public boolean isSectionHeaderPosition(int position) {
        return mSections.get(position) != null;
    }


    @Override
    public long getItemId(int position) {
        return isSectionHeaderPosition(position)
                ? Integer.MAX_VALUE - mSections.indexOfKey(position)
                : super.getItemId(sectionedPositionToPosition(position));
    }


    /**
     * Return the view type of a normal item (not section item)
     *
     * Should be overriden when using multiple view types for
     * the items. It is wise to use the id of the resource
     * since it is used already to identify the view type of
     * the section item
     *
     * @param position
     * @return
     */
    public int getNormalItemViewType(int position) {
        return super.getItemViewType(position);
    }

}