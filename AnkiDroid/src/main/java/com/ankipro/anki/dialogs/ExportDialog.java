
package com.ankipro.anki.dialogs;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ankipro.model.Produto;
import com.ichi2.anki.R;

import java.util.List;

public class ExportDialog extends com.ichi2.anki.dialogs.ExportDialog {

    public static DialogFragment newInstance(List<String> products_keys, List<Produto> produtos, Long did, String dialogMessage) {
        ExportDialog f = new ExportDialog();
        Bundle args = new Bundle();
        args.putLong("did", did);
        args.putString("dialogMessage", dialogMessage);
        f.mProducts = products_keys;
        f.mProducList = produtos;
        f.setArguments(args);
        return f;
    }

    public interface ExportDialogListener {

        //void exportApkg(String path, Long did, boolean includeSched, boolean includeMedia);
        void dismissAllDialogFragments();

        void exportApkg(String path, Long did, boolean includeSched, boolean includeMedia, String selected_id, String selected_key);
    }

    private final int INCLUDE_SCHED = 0;
    private final int INCLUDE_MEDIA = 1;
    private boolean mIncludeSched = false;
    private boolean mIncludeMedia = false;
    private List<String> mProducts;
    private List<Produto> mProducList;

    /**
     * A set of dialogs which deal with importing a file
     *
     * @param did An integer which specifies which of the sub-dialogs to show
     * @param dialogMessage An optional string which can be used to show a custom message or specify import path
     */
    /*public static ExportDialog newInstance(@NonNull String dialogMessage, Long did) {
        ExportDialog f = new ExportDialog();
        Bundle args = new Bundle();
        args.putLong("did", did);
        args.putString("dialogMessage", dialogMessage);
        f.setArguments(args);
        return f;
    }*/


    public static ExportDialog newInstance(List<String> products_keys, List<Produto> produtos, @NonNull String dialogMessage) {
        ExportDialog f = new ExportDialog();
        Bundle args = new Bundle();
        args.putString("dialogMessage", dialogMessage);
        f.mProducts = products_keys;
        f.mProducList = produtos;
        f.setArguments(args);
        return f;
    }


    @Override
    public MaterialDialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        final Long did = getArguments().getLong("did", -1L);
        Integer[] checked;
        if (did != -1L) {
            mIncludeSched = false;
            checked = new Integer[]{};
        } else {
            mIncludeSched = true;
            checked = new Integer[]{ INCLUDE_SCHED };
        }
        final String[] items = { res.getString(R.string.export_include_schedule),
                res.getString(R.string.export_include_media) };

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                .title(R.string.export)
                .content(getArguments().getString("dialogMessage"))
                .positiveText(android.R.string.ok)
                .negativeText(android.R.string.cancel)
                .cancelable(true)
                .items(mProducts)
                .alwaysCallMultiChoiceCallback()
                .itemsCallbackSingleChoice(2, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence charSequence) {


                                /**
                             * If you use alwaysCallSingleChoiceCallback(), which is discussed below,
                             * returning false here won't allow the newly selected radio button to actually be selected.
                             **/
                            if (charSequence != null) {
                                String selected_key = "";
                                String selected_id = "";
                                for (Produto produto : mProducList) {
                                    if (produto.getKey().equals(charSequence.toString())) {
                                        selected_key = produto.isMine() ? "mine" : "other";
                                        selected_id = Integer.toString(produto.getId());
                                    }
                                }
                                ((ExportDialogListener) getActivity()).exportApkg(null, did != -1L ? did : null, mIncludeSched, mIncludeSched,selected_id, selected_key);
                                dismissAllDialogFragments();
                                return true;
                            } else {
                                return false;
                            }
                        }
                    });
                /*.onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        ((ExportDialogListener) getActivity())
                                .exportApkg(null, did != -1L ? did : null, mIncludeSched, mIncludeMedia);
                        dismissAllDialogFragments();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dismissAllDialogFragments();
                    }
                });*/
        return builder.show();
    }


    public void dismissAllDialogFragments() {
        ((ExportDialogListener) getActivity()).dismissAllDialogFragments();
    }

}
