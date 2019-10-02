package chakib.com.instantchat;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

     //fireBaseAuthignInRc
    public static final int RC_SIGN_IN = 1;
    //photos Rc
    private static final int RC_PHOTO_PICKER =  2;

    private LinearLayout mLayout;
    private TextView mErrorMessage;
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    //FireBase instance
    private FirebaseDatabase mFireBaseDataBase;
    //Reference to a specific part of the DataBase
    private DatabaseReference mMessagesDataBaseReference;
    //listener to made realTime Data Base response
    private ChildEventListener mChildEventListener;
    //fireBae athentification
    private FirebaseAuth mFireBaseAuth;
    //Authentification Listener
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    //FireBase storage
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;
    //Firease messaging
    private FirebaseMessaging mFirebaseMessaging;
    //Firebase remoteConfig
    private FirebaseRemoteConfig mFirebaseRemteConfig;

    public boolean testFrag =false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;
        //get instance of th dataBase n' the child for messages
        mFireBaseDataBase = FirebaseDatabase.getInstance();
        mFireBaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseMessaging  =FirebaseMessaging.getInstance();
        mFirebaseRemteConfig = FirebaseRemoteConfig.getInstance();

        mMessagesDataBaseReference = mFireBaseDataBase.getReference().child("messages");
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");
        //mChatPhotosStorageReference = mFirebaseStorage.getReferenceFromUrl("gs://instantchat-55590.appspot.com/chat_photos");

        // Initialize references to views
        mLayout = (LinearLayout) findViewById(R.id.linearLayout);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mErrorMessage = (TextView) findViewById(R.id.ErrorTextView);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDataBaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        mErrorMessage.setVisibility(View.INVISIBLE);

        //authStateListener

            mAuthStateListener = new FirebaseAuth.AuthStateListener() {
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user != null) {
                        //signed in
                        onSignedInInitialize(user.getDisplayName());
                        // Toast.makeText(MainActivity.this,"you're now signed in. welcome to our chat Room",Toast.LENGTH_SHORT).show();
                    } else {
                        //signed out
                        onSignedOutCleanup();
                        startActivityForResult(
                                AuthUI.getInstance()
                                        .createSignInIntentBuilder()
                                        .setIsSmartLockEnabled(false) //we don't need to save user Logging
                                        .setAvailableProviders(
                                                Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                                        new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()/*,
                                                    new AuthUI.IdpConfig.Builder(AuthUI.FACEBOOK_PROVIDER).build()*/))
                                        .build(),
                                RC_SIGN_IN);

                    }

                }
            };
            FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                    .setDeveloperModeEnabled(BuildConfig.DEBUG).build();
            mFirebaseRemteConfig.setConfigSettings(configSettings);

            Map<String , Object> defaultConfigMap = new HashMap<>();
            defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY,DEFAULT_MSG_LENGTH_LIMIT);
            mFirebaseRemteConfig.setDefaults(defaultConfigMap);

            fetchConfig();



    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.sign_out_menu :
                AuthUI.getInstance().signOut(this);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mAuthStateListener != null)
        mFireBaseAuth.removeAuthStateListener(mAuthStateListener);

        detachDataBaseReadListener();

        mMessageAdapter.clear();
    }
    ConnectFragment connectFragment;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                mLayout.setVisibility(View.VISIBLE);
                //mErrorMessage.setVisibility(View.INVISIBLE);
                Toast.makeText(MainActivity.this, "you're signed in", Toast.LENGTH_SHORT).show();

            } else if (resultCode == RESULT_CANCELED) {
                //Toast.makeText(MainActivity.this, "sign in cancelled", Toast.LENGTH_SHORT).show();

                //mFireBaseAuth.removeAuthStateListener(mAuthStateListener);
                mLayout.setVisibility(View.INVISIBLE);
                //mErrorMessage.setVisibility(View.VISIBLE);
                if(!testFrag)
                {  FragmentManager fragmentManager = getSupportFragmentManager();
                    connectFragment = new ConnectFragment();
                    connectFragment.show(fragmentManager , "dialog");
                    testFrag = true;
                }else
                    {
                        if( connectFragment.isHidden())
                                    testFrag = false;
                    }


            }
        } else if(requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK)
                {
                    Uri selectedImageUri = data.getData();
                    //Get reference to store at chat_photos/<FILENAME>
                    StorageReference photoRef = mChatPhotosStorageReference.child(selectedImageUri.getLastPathSegment());//derniere partie du path qui designe le nom de l'image
                    //upload file to firebase storage
                    photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            Uri downloadUrl = taskSnapshot.getDownloadUrl();
                            FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,downloadUrl.toString());
                            mMessagesDataBaseReference.push().setValue(friendlyMessage);
                            //Toast.makeText(MainActivity.this,"success",Toast.LENGTH_SHORT).show();

                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this,"Fail to Load Photo",Toast.LENGTH_SHORT).show();
                            //Log.w(TAG,"Error fetching config",e);
                        }
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFireBaseAuth.addAuthStateListener(mAuthStateListener);

    }


    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDataBaseReadListener();
    }


    private void onSignedInInitialize(String displayName) {
        mUsername =displayName;
        //mErrorMessage.setVisibility(View.INVISIBLE);
       attachDataBaseReadListener();
    }

    private void attachDataBaseReadListener() {
        if(mChildEventListener == null) {
            //Listener
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }
            };

            mMessagesDataBaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDataBaseReadListener() {
        if(mChildEventListener != null) {
            mMessagesDataBaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }


    private void fetchConfig() {
        long cachExpiration = 3600;

        if(mFirebaseRemteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled())
        {
            cachExpiration = 0;
        }
        mFirebaseRemteConfig.fetch(cachExpiration).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemteConfig.activateFetched();
                applyRetrievedLengthLimit();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG,"Error fetching config",e);
                applyRetrievedLengthLimit();
            }
        });
    }

    private void applyRetrievedLengthLimit() {
        Long friendly_msg_length = mFirebaseRemteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
        Log.d(TAG,FRIENDLY_MSG_LENGTH_KEY +" = " +friendly_msg_length);
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager)  context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }



}