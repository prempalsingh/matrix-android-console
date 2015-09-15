/*
 * Copyright 2015 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.console.adapters;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.console.ConsoleApplication;
import org.matrix.console.R;
import org.matrix.console.activity.CommonActivityUtils;
import org.matrix.console.activity.ImageSliderActivity;
import org.matrix.console.activity.MemberDetailsActivity;
import org.matrix.console.util.SlidableImageInfo;

import java.io.File;
import java.util.ArrayList;

/**
 * An adapter which can display room information.
 */
public class ConsoleMessagesAdapter extends MessagesAdapter {

    public static interface MessageLongClickListener {
        public void onMessageLongClick(int position, Message message);
    }

    public static interface AvatarClickListener {
        public Boolean onAvatarClick(String roomId, String userId);
        public Boolean onAvatarLongClick(String roomId, String userId);
        public Boolean onDisplayNameClick(String userId, String displayName);
    }

    private Handler mUiHandler;
    private MessageLongClickListener mLongClickListener = null;
    private AvatarClickListener mAvatarClickListener = null;

    public ConsoleMessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        super(session, context, mediasCache);

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    public void setMessageLongClickListener(MessageLongClickListener listener) {
        mLongClickListener = listener;
    }

    public void setAvatarClickListener(AvatarClickListener listener) {
        mAvatarClickListener = listener;
    }

    @Override
    public void onAvatarClick(String roomId, String userId){
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onAvatarClick(roomId, userId)) {
                return;
            }
        }

        Intent startRoomInfoIntent = new Intent(mContext, MemberDetailsActivity.class);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, roomId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MEMBER_ID, userId);
        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_MATRIX_ID, mSession.getCredentials().userId);
        mContext.startActivity(startRoomInfoIntent);
    }

    @Override
    public Boolean onAvatarLongClick(String roomId, String userId) {
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onAvatarLongClick(roomId, userId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onSenderNameClick(String userId, String displayName) {
        if (null != mAvatarClickListener) {
            if (mAvatarClickListener.onDisplayNameClick(userId, displayName)) {
                // do something here ?
            }
        }

        // default behaviour
    }

    /**
     * @return the imageMessages list
     */
    private ArrayList<SlidableImageInfo> listImageMessages() {
        ArrayList<SlidableImageInfo> res = new ArrayList<SlidableImageInfo>();

        for(int position = 0; position < getCount(); position++) {
            MessageRow row = this.getItem(position);
            Message message = JsonUtils.toMessage(row.getEvent().content);

            if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                ImageMessage imageMessage = (ImageMessage)message;

                SlidableImageInfo info = new SlidableImageInfo();

                info.mImageUrl = imageMessage.url;
                info.mRotationAngle = imageMessage.getRotation();
                info.mOrientation = imageMessage.getOrientation();
                info.mMimeType = imageMessage.getMimeType();
                info.midentifier = row.getEvent().eventId;
                res.add(info);
            }
        }

        return res;
    }

    /**
     * Returns the imageMessages position in listImageMessages.
     * @param listImageMessages the messages list.
     * @param imageMessage the imageMessage
     * @return the imageMessage position. -1 if not found.
     */
    private int getImageMessagePosition(ArrayList<SlidableImageInfo> listImageMessages, ImageMessage imageMessage) {
        for(int index = 0; index < listImageMessages.size(); index++) {
            if (listImageMessages.get(index).mImageUrl.equals(imageMessage.url)) {
                return index;
            }
        }

        return -1;
    }

    @Override
    public void onImageClick(int position, ImageMessage imageMessage, int maxImageWidth, int maxImageHeight, int rotationAngle){
        if (null != imageMessage.url) {

            ArrayList<SlidableImageInfo> listImageMessages = listImageMessages();
            int listPosition = getImageMessagePosition(listImageMessages, imageMessage);

            if (listPosition >= 0) {
                Intent viewImageIntent = new Intent(mContext, ImageSliderActivity.class);

                viewImageIntent.putExtra(ImageSliderActivity.KEY_THUMBNAIL_WIDTH, maxImageWidth);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_THUMBNAIL_HEIGHT, maxImageHeight);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_INFO_LIST, listImageMessages);
                viewImageIntent.putExtra(ImageSliderActivity.KEY_INFO_LIST_INDEX, listPosition);

                mContext.startActivity(viewImageIntent);
            }
        }
    }

    @Override
    public boolean onImageLongClick(int position, ImageMessage imageMessage, int maxImageWidth, int maxImageHeight, int rotationAngle){
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, imageMessage);
            return true;
        }

        return false;
    }

    @Override
    public void onFileDownloaded(int position, FileMessage fileMessage) {
        // save into the downloads
        File mediaFile = mMediasCache.mediaCacheFile(fileMessage.url, fileMessage.getMimeType());

        if (null != mediaFile) {
            CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, fileMessage.body, fileMessage.getMimeType());
        }
    }

    @Override
    public void onFileClick(int position, FileMessage fileMessage) {
        if (null != fileMessage.url) {
            File mediaFile =  mMediasCache.mediaCacheFile(fileMessage.url, fileMessage.getMimeType());

            // is the file already saved
            if (null != mediaFile) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, fileMessage.body, fileMessage.getMimeType());
                CommonActivityUtils.openMedia(ConsoleApplication.getCurrentActivity(), savedMediaPath, fileMessage.getMimeType());
            }
        }
    }

    @Override
    public boolean onFileLongClick(int position, FileMessage fileMessage) {
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, fileMessage);
            return true;
        }

        return false;
    }

    @Override
    public void onVideoClick(int position, VideoMessage videoMessage) {
        if (null != videoMessage.url) {
            File mediaFile =  mMediasCache.mediaCacheFile(videoMessage.url, videoMessage.getVideoMimeType());

            // is the file already saved
            if (null != mediaFile) {
                String savedMediaPath = CommonActivityUtils.saveMediaIntoDownloads(mContext, mediaFile, videoMessage.body, videoMessage.getVideoMimeType());
                CommonActivityUtils.openMedia(ConsoleApplication.getCurrentActivity(), savedMediaPath, videoMessage.getVideoMimeType());
            } else {
                final String downloadId = mMediasCache.downloadMedia(mContext, videoMessage.url, videoMessage.getVideoMimeType());
                final VideoMessage fVideoMessage = videoMessage;
                final int fPosition = position;

                mMediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadStart(String aDownloadId) {
                        if (downloadId.equals(aDownloadId)) {
                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    ConsoleMessagesAdapter.this.notifyDataSetChanged();
                                }
                            });
                        }
                    }

                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            onVideoClick(fPosition, fVideoMessage);
                        }
                    }
                });
            }
        }
    }

    public boolean onVideoLongClick(int position, VideoMessage videoMessage) {
        if (null != mLongClickListener) {
            mLongClickListener.onMessageLongClick(position, videoMessage);
            return true;
        }

        return false;
    }

    @Override
    public void notifyDataSetChanged() {
        //  do not refresh the room when the application is in background
        // on large rooms, it drains a lot of battery
        if (!ConsoleApplication.isAppInBackground()) {
            super.notifyDataSetChanged();
        }
    }

    public int presenceOnlineColor() {
        return mContext.getResources().getColor(R.color.presence_online);
    }

    public int presenceOfflineColor() {
        return mContext.getResources().getColor(R.color.presence_offline);
    }

    public int presenceUnavailableColor() {
        return mContext.getResources().getColor(R.color.presence_unavailable);
    }
}
