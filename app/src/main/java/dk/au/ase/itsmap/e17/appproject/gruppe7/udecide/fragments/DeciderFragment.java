package dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.fragments;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.Date;
import java.util.Set;

import dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.R;
import dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.helpers.FirebaseHelper;
import dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.models.Poll;
import dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST;

import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.DB_DATE;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.FACEBOOK_FRIENDS_IDS;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.IMAGES_SAVED;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.IMAGE_1;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.IMAGE_1_BITMAP;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.IMAGE_2;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.IMAGE_2_BITMAP;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.LAST_POLL_TIMESTAMP;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.SHARED_PREFERENCES;
import static dk.au.ase.itsmap.e17.appproject.gruppe7.udecide.utils.CONST.STORAGE_IMAGES_PATH;


public class DeciderFragment extends Fragment {

    public static SharedPreferences preferences; //Shared preferences inspired by: https://stackoverflow.com/questions/23024831/android-shared-preferences-example
    double image1Votes, image2Votes;
    Poll currentPoll;
    String questionText;
    CollectionReference pollsCollection;
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReference();
    View view;
    FirebaseHelper firebaseHelper;
    AsyncTask<DownloadImageTask, Void, DownloadedImage> downloadImage1;
    AsyncTask<DownloadImageTask, Void, DownloadedImage> downloadImage2;
    private int tasks = 0;
    private Bitmap image1;
    private Bitmap image2;
    private boolean imagesSaved = false;
    private ImageView firstImg;
    private ImageView secondImg;
    private TextView questionTextTV;
    private TextView myProgressTextTv;
    private ProgressBar lastQuestionResult;
    private FirebaseFirestore db;

    private BroadcastReceiver NewPollmsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            Log.d("receiver", "Got message: " + message);
            //Extract values from intent
            String img1 = intent.getStringExtra(IMAGE_1);
            String img2 = intent.getStringExtra(IMAGE_2);
            currentPoll = intent.getParcelableExtra(CONST.CURRENT_POLL);

            updateQuestionText(currentPoll);
            if (imagesSaved) {
                firstImg.setImageBitmap(image1);
                secondImg.setImageBitmap(image2);
                imagesSaved = false;
            } else {
                getImage1(img1);
                getImage2(img2);
            }
            updateProgessBar();
        }
    };

    private BroadcastReceiver NoMorePollsMsgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setEmptyDecider();
        }
    };
    private BroadcastReceiver UpdatePollReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            currentPoll = intent.getParcelableExtra(CONST.CURRENT_POLL);
            saveLastPollTimestamp(currentPoll.getDate().getTime());
            loadPoll();
        }
    };

    public DeciderFragment() {
        // Required empty public constructor
    }

    private void setEmptyDecider() {
        firstImg.setImageResource(0);
        secondImg.setImageResource(0);
        lastQuestionResult.setVisibility(View.INVISIBLE);
        myProgressTextTv.setVisibility(View.INVISIBLE);
        Activity activity = getActivity();
        if (isAdded() && activity != null) {
            questionTextTV.setText(getText(R.string.no_more_polls));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_decider, container, false);
        firebaseHelper = new FirebaseHelper(getContext());
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(NewPollmsgReceiver, new IntentFilter(CONST.UPDATE_EVENT)); //Listen for a local broadcast with this action
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(NoMorePollsMsgReceiver, new IntentFilter(CONST.NO_MORE_POLLS)); //Listen for a local broadcast with this action
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(UpdatePollReceiver, new IntentFilter(CONST.UPDATE_POLL)); //Listen for a local broadcast with this action

        downloadImage1 = new DownloadImage();
        downloadImage2 = new DownloadImage();
        intitializeUIElements();
        preferences = getActivity().getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);

        firstImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                firstImg.setClickable(false);
                firebaseHelper.incrementImageVotes(CONST.IMAGE_1_VOTE_KEY);
            }
        });

        secondImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                secondImg.setClickable(false);
                firebaseHelper.incrementImageVotes(CONST.IMAGE_2_VOTE_KEY);
            }
        });

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        pollsCollection = db.collection(CONST.DB_POLLS_COLLECTION);

        if (savedInstanceState != null) {
            imagesSaved = savedInstanceState.getBoolean(IMAGES_SAVED);
            image1 = savedInstanceState.getParcelable(IMAGE_1_BITMAP);
            image2 = savedInstanceState.getParcelable(IMAGE_2_BITMAP);
        }

        loadPoll();

        return view;
    }

    @Override
    public void onDestroyView() {
        downloadImage1.cancel(true);
        downloadImage2.cancel(true);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(NewPollmsgReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(NoMorePollsMsgReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(UpdatePollReceiver);
        super.onDestroyView();
    }

    private void intitializeUIElements() {
        myProgressTextTv = view.findViewById(R.id.myTextProgress);
        questionTextTV = view.findViewById(R.id.questionTV);

        lastQuestionResult = view.findViewById(R.id.progressBar);
        lastQuestionResult.setMax(100);
        lastQuestionResult.setProgress(0);

        firstImg = view.findViewById(R.id.firstQuestionImg);
        secondImg = view.findViewById(R.id.secondQuestionImg);
        firstImg.setClickable(false);
        secondImg.setClickable(false);
    }

    private void updateProgessBar() {
        image1Votes = currentPoll.getImage1Votes();
        image2Votes = currentPoll.getImage2Votes();

        double votePercentage = (image1Votes / (image1Votes + image2Votes)) * 100;
        lastQuestionResult.setProgress((int) votePercentage);

        //to set text
        myProgressTextTv.setText((int) image1Votes + "/" + (int) image2Votes);
    }

    private void updateQuestionText(Poll currentPoll) {
        questionText = currentPoll.getQuestion();
        questionTextTV.setText(questionText);
    }

    //Inspired by: https://firebase.google.com/docs/firestore/query-data/get-data
    public void loadPoll() {
        Long lastTimestamp = preferences.getLong(LAST_POLL_TIMESTAMP, 0);
        Date lastDate = new Date(lastTimestamp);
        final Query publicPolls;
        if (lastTimestamp != 0) {
            publicPolls = pollsCollection.whereGreaterThan(DB_DATE, lastDate).orderBy(DB_DATE, Query.Direction.ASCENDING).limit(1);
        } else {
            publicPolls = pollsCollection.orderBy(DB_DATE, Query.Direction.ASCENDING).limit(1);
        }
        final Set<String> facebookFriends = preferences.getStringSet(FACEBOOK_FRIENDS_IDS, null);
        firebaseHelper.getPollData(publicPolls, facebookFriends);
    }

    // https://firebase.google.com/docs/storage/android/download-files#downloading_images_with_firebaseui
    public void getImage1(String imageId) {
        loading(true, firstImg);
        tasks++;
        storageRef.child(STORAGE_IMAGES_PATH + imageId).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                    downloadImage1 = new DownloadImage().execute(new DownloadImageTask(firstImg, uri));
            }
        });
    }

    // https://firebase.google.com/docs/storage/android/download-files#downloading_images_with_firebaseui
    public void getImage2(String imageId) {
        loading(true, secondImg);
        tasks++;
        storageRef.child(STORAGE_IMAGES_PATH + imageId).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                    downloadImage2 = new DownloadImage().execute(new DownloadImageTask(secondImg, uri));
            }
        });
    }

    private void setImage(DownloadedImage downloadedImage) {
        downloadedImage.getImageView().setImageBitmap(downloadedImage.getBitmap());
        loading(false, downloadedImage.getImageView());
        downloadedImage.getImageView().setClickable(true);
        tasks--;
    }

    //Shared preferences inspired by: https://stackoverflow.com/questions/23024831/android-shared-preferences-example
    protected void saveLastPollTimestamp(Long timestamp) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(LAST_POLL_TIMESTAMP, timestamp).apply();
    }

    private void loading(Boolean status, ImageView imageView) {

        final RotateAnimation rotate = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(1000);
        rotate.setRepeatCount(Animation.INFINITE);
        rotate.setInterpolator(new LinearInterpolator());

        if (status) {
            imageView.setImageResource(R.drawable.ic_compare_arrows_black_24dp);
            imageView.setColorFilter(Color.GRAY);
            imageView.startAnimation(rotate);

        } else {
            imageView.clearColorFilter();
            imageView.clearAnimation();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        if (tasks == 0 && questionTextTV.getText() != getText(R.string.no_more_polls)) {
            BitmapDrawable firstImgDrawable = (BitmapDrawable) firstImg.getDrawable();
            BitmapDrawable secondImgDrawable = (BitmapDrawable) secondImg.getDrawable();
            outState.putParcelable(IMAGE_1_BITMAP, firstImgDrawable.getBitmap());
            outState.putParcelable(IMAGE_2_BITMAP, secondImgDrawable.getBitmap());
            outState.putBoolean(IMAGES_SAVED, true);
        } else {
            outState.putBoolean(IMAGES_SAVED, false);
        }
        super.onSaveInstanceState(outState);
    }

    private class DownloadImageTask {
        private ImageView imageView;
        private Uri uri;

        DownloadImageTask(ImageView imageView, Uri uri) {
            this.imageView = imageView;
            this.uri = uri;
        }

        ImageView getImageView() {
            return imageView;
        }

    }

    private class DownloadedImage {
        private Bitmap bitmap;
        private ImageView imageView;

        DownloadedImage(Bitmap bitmap, ImageView imageView) {
            this.bitmap = bitmap;
            this.imageView = imageView;
        }

        Bitmap getBitmap() {
            return bitmap;
        }

        ImageView getImageView() {
            return imageView;
        }
    }

    private class DownloadImage extends AsyncTask<DownloadImageTask, Void, DownloadedImage> {

        @Override
        protected DownloadedImage doInBackground(DownloadImageTask... downloadImageTasks) {

            Bitmap bitmap = null;
            ImageView imageView = null;

            for (DownloadImageTask downloadImageTask : downloadImageTasks) {
                try {
                    imageView = downloadImageTask.getImageView();
                    bitmap = Picasso.with(getContext()).load(downloadImageTask.uri).get();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return new DownloadedImage(bitmap, imageView);
        }

        @Override
        protected void onPostExecute(DownloadedImage downloadedImage) {
            setImage(downloadedImage);
            super.onPostExecute(downloadedImage);
        }
    }
}
