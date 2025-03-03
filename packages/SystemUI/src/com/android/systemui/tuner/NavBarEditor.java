/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

import com.android.systemui.Dependency;

import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.BACK;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.BUTTON_SEPARATOR;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.CLIPBOARD;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.GRAVITY_SEPARATOR;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.HOME;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_CODE_END;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_CODE_START;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.KEY_IMAGE_DELIM;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.MENU_IME_ROTATE;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAVSPACE;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.NAV_BAR_VIEWS;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.RECENT;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.SIZE_MOD_END;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.SIZE_MOD_START;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.WEIGHT_SUFFIX;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.WEIGHT_CENTERED_SUFFIX;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.LEFT;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.RIGHT;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.extractButton;
import static com.android.systemui.statusbar.phone.NavigationBarInflaterView.extractSize;

public class NavBarEditor extends PreferenceFragment implements TunerService.Tunable {
    private static final String TAG = "NavBarEditor";
    private static final int READ_REQUEST = 42;

    private static final float PREVIEW_SCALE = .95f;
    private static final float PREVIEW_SCALE_LANDSCAPE = .75f;

    private NavBarAdapter mNavBarAdapter;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.nav_bar_tuner, container, false);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
        return view;
    }

    private void notifyChanged() {
        Settings.Secure.putString(getContext().getContentResolver(),
                        NAV_BAR_VIEWS, mNavBarAdapter.getNavString());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        final Context context = getContext();
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        mNavBarAdapter = new NavBarAdapter(context);
        recyclerView.setAdapter(mNavBarAdapter);
        recyclerView.addItemDecoration(new Dividers(context));
        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(mNavBarAdapter.mCallbacks);
        mNavBarAdapter.setTouchHelper(itemTouchHelper);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        setHasOptionsMenu(true);
        Dependency.get(TunerService.class).addTunable(this, NAV_BAR_VIEWS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Dependency.get(TunerService.class).removeTunable(this);
    }

    @Override
    public void onTuningChanged(String key, String navLayout) {
        if (!NAV_BAR_VIEWS.equals(key)) return;
        Context context = getContext();
        if (navLayout == null) {
            navLayout = context.getString(R.string.config_navBarLayout);
        }
        String[] views = navLayout.split(GRAVITY_SEPARATOR);
        String[] groups = new String[] { NavBarAdapter.START, NavBarAdapter.CENTER,
                NavBarAdapter.END};
        CharSequence[] groupLabels = new String[] { getString(R.string.start),
                getString(R.string.center), getString(R.string.end) };
        mNavBarAdapter.clear();
        for (int i = 0; i < 3; i++) {
            mNavBarAdapter.addButton(groups[i], groupLabels[i]);
            for (String button : views[i].split(BUTTON_SEPARATOR)) {
                mNavBarAdapter.addButton(button, getLabel(button, context));
            }
        }
        mNavBarAdapter.addButton(NavBarAdapter.ADD, getString(R.string.add_button));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.nav_bar_tuner_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.nav_bar_reset) {
            Settings.Secure.putString(getContext().getContentResolver(),
                    NAV_BAR_VIEWS, null);
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private static CharSequence getLabel(String button, Context context) {
        if (button.startsWith(HOME)) {
            return context.getString(R.string.accessibility_home);
        } else if (button.startsWith(BACK)) {
            return context.getString(R.string.accessibility_back);
        } else if (button.startsWith(RECENT)) {
            return context.getString(R.string.accessibility_recent);
        } else if (button.startsWith(NAVSPACE)) {
            return context.getString(R.string.space);
        } else if (button.startsWith(LEFT)) {
            return context.getString(R.string.left);
        } else if (button.startsWith(RIGHT)) {
            return context.getString(R.string.right);
        /*} else if (button.equals(MENU_IME_ROTATE)) {
            return context.getString(R.string.menu_ime);*/
        /*} else if (button.startsWith(CLIPBOARD)) {
            return context.getString(R.string.clipboard);*/
        /*} else if (button.startsWith(KEY)) {
            return context.getString(R.string.keycode);*/
        }
        return button;
    }

    private static class Holder extends RecyclerView.ViewHolder {
        private TextView title;

        public Holder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(android.R.id.title);
        }
    }

    private static class Dividers extends RecyclerView.ItemDecoration {
        private final Drawable mDivider;

        public Dividers(Context context) {
            TypedValue value = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.listDivider, value, true);
            mDivider = context.getDrawable(value.resourceId);
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            super.onDraw(c, parent, state);
            final int left = parent.getPaddingLeft();
            final int right = parent.getWidth() - parent.getPaddingRight();

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child
                        .getLayoutParams();
                final int top = child.getBottom() + params.bottomMargin;
                final int bottom = top + mDivider.getIntrinsicHeight();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }

    /*private void selectImage() {
        startActivityForResult(KeycodeSelectionHelper.getSelectImageIntent(), READ_REQUEST);
    }*/

    /*@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == READ_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            final Uri uri = data.getData();
            final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
            mNavBarAdapter.onImageSelected(uri);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }*/

    private class NavBarAdapter extends RecyclerView.Adapter<Holder>
            implements View.OnClickListener {

        private static final String START = "start";
        private static final String CENTER = "center";
        private static final String END = "end";
        private static final String ADD = "add";

        private static final int ADD_ID = 0;
        private static final int BUTTON_ID = 1;
        private static final int CATEGORY_ID = 2;

        private List<String> mButtons = new ArrayList<>();
        private List<CharSequence> mLabels = new ArrayList<>();
        private int mCategoryLayout;
        private int mButtonLayout;
        private ItemTouchHelper mTouchHelper;

        // Stored keycode while we wait for image selection on a KEY.
        private int mKeycode;

        public NavBarAdapter(Context context) {
            TypedArray attrs = context.getTheme().obtainStyledAttributes(null,
                    android.R.styleable.Preference, android.R.attr.preferenceStyle, 0);
            mButtonLayout = attrs.getResourceId(android.R.styleable.Preference_layout, 0);
            attrs = context.getTheme().obtainStyledAttributes(null,
                    android.R.styleable.Preference, android.R.attr.preferenceCategoryStyle, 0);
            mCategoryLayout = attrs.getResourceId(android.R.styleable.Preference_layout, 0);
        }

        public void setTouchHelper(ItemTouchHelper itemTouchHelper) {
            mTouchHelper = itemTouchHelper;
        }

        public void clear() {
            mButtons.clear();
            mLabels.clear();
            notifyDataSetChanged();
        }

        public void addButton(String button, CharSequence label) {
            mButtons.add(button);
            mLabels.add(label);
            notifyItemInserted(mLabels.size() - 1);
        }

        public boolean hasHomeButton() {
            final int N = mButtons.size();
            for (int i = 0; i < N; i++) {
                if (mButtons.get(i).startsWith(HOME)) {
                    return true;
                }
            }
            return false;
        }

        private String getNavString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < mButtons.size() - 1; i++) {
                String button = mButtons.get(i);
                if (button.equals(CENTER) || button.equals(END)) {
                    if (builder.length() == 0 || builder.toString().endsWith(GRAVITY_SEPARATOR)) {
                        // No start or center buttons, fill with a space.
                        builder.append(NAVSPACE);
                    }
                    builder.append(GRAVITY_SEPARATOR);
                    continue;
                } else if (builder.length() != 0 && !builder.toString().endsWith(
                        GRAVITY_SEPARATOR)) {
                    builder.append(BUTTON_SEPARATOR);
                }
                builder.append(button);
            }
            if (builder.toString().endsWith(GRAVITY_SEPARATOR)) {
                // No end buttons, fill with space.
                builder.append(NAVSPACE);
            }
            return builder.toString();
        }

        @Override
        public int getItemViewType(int position) {
            String button = mButtons.get(position);
            if (button.equals(START) || button.equals(CENTER) || button.equals(END)) {
                return CATEGORY_ID;
            }
            if (button.equals(ADD)) {
                return ADD_ID;
            }
            return BUTTON_ID;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            final LayoutInflater inflater = LayoutInflater.from(context);
            final View view = inflater.inflate(getLayoutId(viewType), parent, false);
            if (viewType == BUTTON_ID) {
                inflater.inflate(R.layout.nav_control_widget,
                        (ViewGroup) view.findViewById(android.R.id.widget_frame));
            }
            return new Holder(view);
        }

        private int getLayoutId(int viewType) {
            if (viewType == CATEGORY_ID) {
                return mCategoryLayout;
            }
            return mButtonLayout;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            holder.title.setText(mLabels.get(position));
            if (holder.getItemViewType() == BUTTON_ID) {
                bindButton(holder, position);
            } else if (holder.getItemViewType() == ADD_ID) {
                bindAdd(holder);
            }
        }

        private void bindAdd(Holder holder) {
            TypedValue value = new TypedValue();
            final Context context = holder.itemView.getContext();
            context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true);
            final ImageView icon = (ImageView) holder.itemView.findViewById(android.R.id.icon);
            icon.setImageResource(R.drawable.ic_add);
            icon.setImageTintList(ColorStateList.valueOf(context.getColor(value.resourceId)));
            holder.itemView.findViewById(android.R.id.summary).setVisibility(View.GONE);
            holder.itemView.setClickable(true);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddDialog(v.getContext());
                }
            });
        }

        private void bindButton(final Holder holder, int position) {
            final Context context = holder.itemView.getContext();
            holder.itemView.findViewById(android.R.id.icon_frame).setVisibility(View.GONE);
            holder.itemView.findViewById(android.R.id.summary).setVisibility(View.GONE);

            if (mLabels.get(position).equals(context.getString(R.string.accessibility_home))) {
                holder.itemView.findViewById(R.id.close).setVisibility(View.INVISIBLE);
            } else {
                holder.itemView.findViewById(R.id.close).setVisibility(View.VISIBLE);
                bindClick(holder.itemView.findViewById(R.id.close), holder);
            }
            bindClick(holder.itemView.findViewById(R.id.width), holder);
            holder.itemView.findViewById(R.id.drag).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    mTouchHelper.startDrag(holder);
                    return true;
                }
            });
        }

        private void showAddDialog(final Context context) {
            final String[] options = new String[] {
                    BACK, HOME, RECENT, NAVSPACE, LEFT, RIGHT
            };
            final CharSequence[] labels = new CharSequence[options.length];
            for (int i = 0; i < options.length; i++) {
                labels[i] = getLabel(options[i], context);
            }
            new AlertDialog.Builder(context)
                    .setTitle(R.string.select_button)
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            /*if (KEY.equals(options[which])) {
                                showKeyDialogs(context);
                            } else {*/
                                int index = mButtons.size() - 1;
                                //showAddedMessage(context, options[which]);
                                mButtons.add(index, options[which]);
                                mLabels.add(index, labels[which]);

                                notifyItemInserted(index);
                                notifyChanged();
                            //}
                        }
                    }).setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        /*private void onImageSelected(Uri uri) {
            int index = mButtons.size() - 1;
            mButtons.add(index, KEY + KEY_CODE_START + mKeycode + KEY_IMAGE_DELIM + uri.toString()
                    + KEY_CODE_END);
            mLabels.add(index, getLabel(KEY, getContext()));

            notifyItemInserted(index);
            notifyChanged();
        }*/

        /*private void showKeyDialogs(final Context context) {
            final KeycodeSelectionHelper.OnSelectionComplete listener =
                    new KeycodeSelectionHelper.OnSelectionComplete() {
                        @Override
                        public void onSelectionComplete(int code) {
                            mKeycode = code;
                            selectImage();
                        }
                    };
            new AlertDialog.Builder(context)
                    .setTitle(R.string.keycode)
                    .setMessage(R.string.keycode_description)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            KeycodeSelectionHelper.showKeycodeSelect(context, listener);
                        }
                    }).show();
        }*/

        /*private void showAddedMessage(Context context, String button) {
            if (CLIPBOARD.equals(button)) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.clipboard)
                        .setMessage(R.string.clipboard_description)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }*/

        private void bindClick(View view, Holder holder) {
            view.setOnClickListener(this);
            view.setTag(holder);
        }

        @Override
        public void onClick(View v) {
            Holder holder = (Holder) v.getTag();
            if (v.getId() == R.id.width) {
                showWidthDialog(holder, v.getContext());
            } else if (v.getId() == R.id.close) {
                int position = holder.getAdapterPosition();
                mButtons.remove(position);
                mLabels.remove(position);
                notifyItemRemoved(position);
                notifyChanged();
            }
        }

        private void showWidthDialog(final Holder holder, Context context) {
            final String buttonSpec = mButtons.get(holder.getAdapterPosition());
            String sizeStr = extractSize(buttonSpec);
            float amount = 1.0f;
            if (sizeStr != null && sizeStr.contains(WEIGHT_SUFFIX)) {
                amount = Float.parseFloat(sizeStr.substring(0, sizeStr.indexOf(WEIGHT_SUFFIX)));
            }
            final boolean isCenterButton = buttonSpec.startsWith(HOME) || buttonSpec.startsWith(BACK) ||
                    buttonSpec.startsWith(RECENT);
            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.adjust_button_width)
                    .setView(R.layout.nav_width_view)
                    .setNegativeButton(android.R.string.cancel, null).create();
            dialog.setButton(DialogInterface.BUTTON_POSITIVE,
                    context.getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            final String button = extractButton(buttonSpec);
                            SeekBar seekBar = (SeekBar) dialog.findViewById(R.id.seekbar);
                            float newAmount = getAmountValue(seekBar.getProgress());
                            if (newAmount == 1f) {
                                mButtons.set(holder.getAdapterPosition(), button);
                            } else {
                                mButtons.set(holder.getAdapterPosition(), button
                                        + SIZE_MOD_START + newAmount + (isCenterButton ?
                                        WEIGHT_CENTERED_SUFFIX : WEIGHT_SUFFIX) + SIZE_MOD_END);
                            }
                            notifyChanged();
                        }
                    });
            dialog.show();
            SeekBar seekBar = (SeekBar) dialog.findViewById(R.id.seekbar);
            // Range is .25 - 1.75.
            seekBar.setMax(6);
            seekBar.setProgress(getProgressValue(amount));
        }

        private int getProgressValue(float amount) {
            if (amount == .25f) {
                return 0;
            }
            if (amount == .5f) {
                return 1;
            }
            if (amount == .75f) {
                return 2;
            }
            if (amount == 1f) {
                return 3;
            }
            if (amount == 1.25f) {
                return 4;
            }
            if (amount == 1.5f) {
                return 5;
            }
            if (amount == 1.75f) {
                return 6;
            }
            return 3;
        }

        private float getAmountValue(int progress) {
            if (progress == 0) {
                return .25f;
            }
            if (progress == 1) {
                return .5f;
            }
            if (progress == 2) {
                return .75f;
            }
            if (progress == 3) {
                return 1f;
            }
            if (progress == 4) {
                return 1.25f;
            }
            if (progress == 5) {
                return 1.5f;
            }
            if (progress == 6) {
                return 1.75f;
            }
            return 1f;
        }

        @Override
        public int getItemCount() {
            return mButtons.size();
        }

        private final ItemTouchHelper.Callback mCallbacks = new ItemTouchHelper.Callback() {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() != BUTTON_ID) {
                    return makeMovementFlags(0, 0);
                }
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(dragFlags, 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                    RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (to == 0) {
                    // Can't go above the top.
                    return false;
                }
                move(from, to, mButtons);
                move(from, to, mLabels);
                notifyItemMoved(from, to);
                return true;
            }

            private <T> void move(int from, int to, List<T> list) {
                list.add(from > to ? to : to + 1, list.get(from));
                list.remove(from > to ? from + 1 : from);
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // Don't care.
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                notifyChanged();
            }
        };
    }
}
