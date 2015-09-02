package com.crossbowffs.nekosms.app;

import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.data.SmsMessageData;
import com.crossbowffs.nekosms.database.BlockedSmsDbLoader;
import com.crossbowffs.nekosms.database.InboxSmsDbLoader;
import com.crossbowffs.nekosms.utils.Xlog;

/* package */ class BlockedSmsListAdapter extends RecyclerCursorAdapter<BlockedSmsListAdapter.BlockedSmsListItemHolder> {
    public static class BlockedSmsListItemHolder extends RecyclerView.ViewHolder {
        public final TextView mSenderTextView;
        public final TextView mTimeSentTextView;
        public final TextView mBodyTextView;
        public SmsMessageData mMessageData;

        public BlockedSmsListItemHolder(View itemView) {
            super(itemView);

            mSenderTextView = (TextView)itemView.findViewById(R.id.listitem_blockedsms_list_sender_textview);
            mTimeSentTextView = (TextView)itemView.findViewById(R.id.listitem_blockedsms_list_timesent_textview);
            mBodyTextView = (TextView)itemView.findViewById(R.id.listitem_blockedsms_list_body_textview);
        }
    }

    private static final String TAG = BlockedSmsListAdapter.class.getSimpleName();

    private final BlockedSmsListActivity mActivity;
    private final CoordinatorLayout mCoordinatorLayout;
    private final String mMessageDetailsFormatString;
    private int[] mColumns;

    public BlockedSmsListAdapter(BlockedSmsListActivity activity) {
        mActivity = activity;
        mMessageDetailsFormatString = activity.getString(R.string.format_message_details);
        mCoordinatorLayout = (CoordinatorLayout)activity.findViewById(R.id.activity_blockedsms_list_root);
    }

    @Override
    public BlockedSmsListItemHolder onCreateViewHolder(ViewGroup group, int i) {
        LayoutInflater layoutInflater = LayoutInflater.from(mActivity);
        View view = layoutInflater.inflate(R.layout.listitem_blockedsms_list, group, false);
        return new BlockedSmsListItemHolder(view);
    }

    @Override
    public void onBindViewHolder(BlockedSmsListItemHolder holder, Cursor cursor) {
        if (mColumns == null) {
            mColumns = BlockedSmsDbLoader.getColumns(cursor);
        }

        final SmsMessageData messageData = BlockedSmsDbLoader.getMessageData(cursor, mColumns, holder.mMessageData);
        holder.mMessageData = messageData;

        String sender = messageData.getSender();
        long timeSent = messageData.getTimeSent();
        String body = messageData.getBody();
        CharSequence timeSentString = DateUtils.getRelativeTimeSpanString(mActivity, timeSent);

        holder.mSenderTextView.setText(sender);
        holder.mTimeSentTextView.setText(timeSentString);
        holder.mBodyTextView.setText(body);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessageDetailsDialog(messageData);
            }
        });
    }

    private void showMessageDetailsDialog(final SmsMessageData messageData) {
        final long smsId = messageData.getId();
        String sender = messageData.getSender();
        String body = messageData.getBody();
        long timeSent = messageData.getTimeSent();
        String escapedBody = Html.escapeHtml(body).replace("&#10;", "<br>");
        String timeSentString = DateUtils.getRelativeDateTimeString(
            mActivity, timeSent, 0, DateUtils.WEEK_IN_MILLIS, 0).toString();
        Spanned html = Html.fromHtml(String.format(
            mMessageDetailsFormatString, sender, timeSentString, escapedBody));

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity)
            .setMessage(html)
            .setNeutralButton(R.string.close, null)
            .setPositiveButton(R.string.restore, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    restoreSms(smsId);
                }
            })
            .setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteSms(smsId);
                }
            });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void restoreSms(long smsId) {
        final SmsMessageData messageData = BlockedSmsDbLoader.loadAndDeleteMessage(mActivity, smsId);
        if (messageData == null) {
            Xlog.e(TAG, "Failed to restore message: could not load data");
            return;
        }

        final Uri inboxSmsUri = InboxSmsDbLoader.writeMessage(mActivity, messageData);
        Snackbar.make(mCoordinatorLayout, R.string.message_restored, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    InboxSmsDbLoader.deleteMessage(mActivity, inboxSmsUri);
                    BlockedSmsDbLoader.writeMessage(mActivity, messageData);
                }
            })
            .show();
    }

    private void deleteSms(long smsId) {
        final SmsMessageData messageData = BlockedSmsDbLoader.loadAndDeleteMessage(mActivity, smsId);
        if (messageData == null) {
            Xlog.e(TAG, "Failed to delete message: could not load data");
            return;
        }

        Snackbar.make(mCoordinatorLayout, R.string.message_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BlockedSmsDbLoader.writeMessage(mActivity, messageData);
                }
            })
            .show();
    }
}