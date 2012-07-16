
package com.android.mms.templates;

import com.android.mms.templates.TemplatesDb.TemplateMetaData;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import com.android.mms.R;

public class TemplatesListActivity extends Activity {

    // codes for dialogs
    private static final int DIALOG_CANCEL_CONFIRM = 2;

    // codes for activity results
    private static final int TEMPLATE_EDITOR = 1;

    private Cursor mCursor;

    private ListView mListView;

    private Button mNewTemplateButton;

    private long mTemplateToDeleteId;

    private TemplatesDb mDb;

    protected void createNewTemplate() {
        final Intent intent = new Intent(this, TemplateEditor.class);
        intent.putExtra(TemplateEditor.KEY_DISPLAY_TYPE, TemplateEditor.DISPLAY_TYPE_NEW_TEMPLATE);
        startActivityForResult(intent, TEMPLATE_EDITOR);
    }

    protected void doDeleteTemplate() {
        final TemplatesDb db = new TemplatesDb(this);
        db.open();
        db.deleteTemplate(mTemplateToDeleteId);
        db.close();
        TemplateGesturesLibrary.getStore(this).removeEntry(String.valueOf(mTemplateToDeleteId));
        mCursor.requery();
    }

    protected void modifyTemplate(long id) {
        final Intent intent = new Intent(this, TemplateEditor.class);
        intent.putExtra(TemplateEditor.KEY_DISPLAY_TYPE, TemplateEditor.DISPLAY_TYPE_EDIT_TEMPLATE);
        intent.putExtra(TemplateEditor.KEY_TEMPLATE_ID, id);
        startActivityForResult(intent, TEMPLATE_EDITOR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == TEMPLATE_EDITOR) {
            if (requestCode == RESULT_OK) {
                mCursor.requery();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        final int itemId = item.getItemId();

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
                .getMenuInfo();

        switch (itemId) {
            case R.id.template_delete:
                mTemplateToDeleteId = info.id;
                showDialog(DIALOG_CANCEL_CONFIRM);
                break;

            case R.id.template_edit:
                modifyTemplate(info.id);
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.templates_list);

        mListView = (ListView) findViewById(R.id.template_lv);
        mNewTemplateButton = (Button) findViewById(R.id.create_new_message_template);

        mNewTemplateButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                createNewTemplate();
            }
        });

        mDb = new TemplatesDb(this);
        mDb.open();
        mCursor = mDb.getAllTemplates();

        startManagingCursor(mCursor);

        final SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, mCursor, new String[] {
                    TemplateMetaData.TEMPLATE_TEXT
                }, new int[] {
                    android.R.id.text1
                });

        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long id) {
                modifyTemplate(id);
            }
        });

        registerForContextMenu(mListView);

    }

    @Override
    protected void onDestroy() {

        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }

        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }

        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

        if (v == mListView) {
            getMenuInflater().inflate(R.menu.templates_list_context_menu, menu);
            menu.setHeaderTitle(R.string.template_ctx_menu_title);
        }

        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {

        switch (id) {
            case DIALOG_CANCEL_CONFIRM:
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.template_cancel_confirm_title);
                builder.setMessage(R.string.template_cancel_confirm_text);
                builder.setPositiveButton(R.string.yes, new Dialog.OnClickListener() {

                    public void onClick(DialogInterface arg0, int arg1) {
                        doDeleteTemplate();
                    }
                });
                builder.setNegativeButton(R.string.no, new Dialog.OnClickListener() {

                    public void onClick(DialogInterface dialog, int arg1) {
                        dialog.dismiss();
                    }
                });
                builder.setCancelable(true);
                return builder.create();

            default:
                break;
        }

        return super.onCreateDialog(id, args);
    }

}
