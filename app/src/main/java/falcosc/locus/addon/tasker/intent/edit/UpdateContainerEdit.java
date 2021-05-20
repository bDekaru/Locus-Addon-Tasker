package falcosc.locus.addon.tasker.intent.edit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog.Builder;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.*;

import falcosc.locus.addon.tasker.R;
import falcosc.locus.addon.tasker.intent.LocusActionType;
import falcosc.locus.addon.tasker.utils.Const;
import falcosc.locus.addon.tasker.utils.LocusCache;
import falcosc.locus.addon.tasker.utils.TaskerField;

public class UpdateContainerEdit extends TaskerEditActivity {

    private Set<String> mStoredFieldSelection;
    private ArrayAdapter<TaskerFieldSelection> mArrayAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocusCache locusCache = LocusCache.getInstance(getApplication());

        mStoredFieldSelection = new HashSet<>();

        if (savedInstanceState == null) {

            Bundle taskerBundle = getIntent().getBundleExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE);
            if (taskerBundle != null) {
                String[] savedSelectedFieldsArray = taskerBundle.getStringArray(Const.INTENT_EXTRA_FIELD_LIST);
                if (savedSelectedFieldsArray != null) {
                    mStoredFieldSelection.addAll(Arrays.asList(savedSelectedFieldsArray));
                }
            }
        }

        ArrayList<TaskerField> updateContainerFields = locusCache.mUpdateContainerFields;

        //ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
        mArrayAdapter = new UpdateContainerArrayAdapter(this);

        setContentView(R.layout.edit_list_view);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = getResources().getDimensionPixelSize(R.dimen.edit_list_dialog_width);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        if(params.width > metrics.widthPixels){
            params.width = metrics.widthPixels;
        }
        getWindow().setAttributes(params);

        ListView listView = findViewById(R.id.listView);

        listView.setAdapter(mArrayAdapter);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);

        for (TaskerField field : updateContainerFields) {
            mArrayAdapter.add(new TaskerFieldSelection(field, mStoredFieldSelection.contains(field.mTaskerName)));
        }
    }

    private boolean isCalcNavigationProgressNew(@NonNull Set<String> previousFieldSelection) {
        LocusCache locusCache = LocusCache.getInstance(getApplication());
        boolean isPrevWithoutNavigation = Collections.disjoint(previousFieldSelection, locusCache.mLocationProgressKeys);
        boolean isNewNavigation = !Collections.disjoint(mStoredFieldSelection, locusCache.mLocationProgressKeys);

        return isPrevWithoutNavigation && isNewNavigation;
    }

    @Nullable
    private Dialog createHintsDialog(@NonNull Set<String> previousFieldSelection) {
        Dialog hintsDialog = null;
        if (isCalcNavigationProgressNew(previousFieldSelection)) {
            //track required was not selected and got selected this time, create hint:
            Builder builder = new Builder(this);
            builder.setView(R.layout.calc_remain_elevation);
            builder.setPositiveButton(R.string.ok, null);
            hintsDialog = builder.create();
        }
        return hintsDialog;
    }

    @Override
    void onApply() {
        Set<String> previousFieldSelection = mStoredFieldSelection;
        mStoredFieldSelection = new LinkedHashSet<>();

        ArrayList<TaskerField> selectedFields = new ArrayList<>();

        int checkedItemsCount = mArrayAdapter.getCount();
        for (int i = 0; i < checkedItemsCount; ++i) {
            TaskerFieldSelection field = mArrayAdapter.getItem(i);
            if (field.mIsChecked) {
                selectedFields.add(field);
                mStoredFieldSelection.add(field.mTaskerName);
            }
        }
        finish(createResultIntent(LocusActionType.UPDATE_CONTAINER_REQUEST, selectedFields), createHintsDialog(previousFieldSelection));
    }

    public static class TaskerFieldSelection extends TaskerField {
        public boolean mIsChecked;
        public SpannableStringBuilder mHelpText;

        public TaskerFieldSelection(TaskerField field, boolean isChecked){
            super(field);
            mIsChecked = isChecked;
            mHelpText = null;
        }

        public Spannable getHelpText(Context context) {
            if(!mIsChecked){
                return null;
            }
            if(mHelpText != null){
                return mHelpText;
            }
            mHelpText = new SpannableStringBuilder();
            mHelpText.append("%" + mTaskerName + " ", new StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            try {
                Field idField = R.string.class.getDeclaredField("uc_" + mTaskerName); //NON-NLS
                mHelpText.append(context.getResources().getText(idField.getInt(idField)));
            } catch (Exception ignored) {}
            return mHelpText;
        }
    }

    public static class ViewHolder{
        CheckedTextView label;
        TextView desc;
    }

    private static final class UpdateContainerArrayAdapter extends ArrayAdapter<TaskerFieldSelection> {
        UpdateContainerArrayAdapter(Context context) {
            super(context, R.layout.list_item_multiple_choice);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            TaskerFieldSelection field = getItem(position);

            ViewHolder viewHolder;
            if (view == null) {
                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                view = inflater.inflate(R.layout.list_item_multiple_choice, parent, false);
                viewHolder.label = view.findViewById(android.R.id.text1);
                viewHolder.desc = view.findViewById(android.R.id.text2);
                view.setTag(viewHolder);
            } else {
                //noinspection CastToConcreteClass
                viewHolder = (ViewHolder) view.getTag();
            }
            viewHolder.label.setText(field.mLabel);
            viewHolder.label.setChecked(field.mIsChecked);
            viewHolder.desc.setText(field.getHelpText(getContext()));
            setVisibile(viewHolder.desc, field.mIsChecked);
            view.setOnClickListener((v) -> {
                field.mIsChecked = !field.mIsChecked;
                viewHolder.label.setChecked(field.mIsChecked);
                setVisibile(viewHolder.desc, field.mIsChecked);
            });

            return view;
        }

        private static void setVisibile(View view, boolean visible) {
            if (visible) {
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }
    }
}
