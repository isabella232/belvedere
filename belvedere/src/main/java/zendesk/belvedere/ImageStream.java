package zendesk.belvedere;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import zendesk.belvedere.ui.R;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ImageStream extends AppCompatActivity
        implements ImageStreamMvp.View, ImageStreamAdapter.Delegate {

    private static final int PERMISSION_REQUEST_CODE = 9842;

    private static final String VIEW_STATE = "view_state";

    private ImageStreamMvp.Presenter presenter;
    private ImageStreamDataSource dataSource;
    private ImageStreamMvp.ViewState viewState;

    private View bottomSheet, dismissArea;
    private FloatingActionMenu floatingActionMenu;
    private RecyclerView imageList;
    private Toolbar toolbar;
    private MenuItem galleryMenuItem;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private ImageStreamAdapter imageStreamAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in, R.anim.no_change);

        setContentView(R.layout.activity_image_stream);
        bindViews();

        Utils.dimStatusBar(this);
        Utils.hideToolbar(this);

        viewState = new ImageStreamMvp.ViewState(BottomSheetBehavior.STATE_COLLAPSED);
        if (savedInstanceState != null && savedInstanceState.getParcelable(VIEW_STATE) != null) {
            viewState = savedInstanceState.getParcelable(VIEW_STATE);
        }

        PermissionStorage preferences = new PermissionStorage(this);
        final BelvedereUi.UiConfig startConfig = BelvedereUi.getUiConfig(getIntent().getExtras());
        final ImageStreamMvp.Model model = new ImageStreamModel(this, startConfig, preferences);
        this.dataSource = new ImageStreamDataSource();

        presenter = new ImageStreamPresenter(model, this, dataSource);
        presenter.init();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                presenter.permissionGranted(true, permissions[0]);
            } else if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]);
                if (!showRationale) {
                    presenter.dontAskForPermissionAgain(permissions[0]);
                } else {
                    presenter.permissionGranted(false, permissions[0]);
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Belvedere.from(this).getFilesFromActivityOnResult(requestCode, resultCode, data, new Callback<List<MediaResult>>() {
            @Override
            public void success(List<MediaResult> result) {
                finishWithResult(result);
            }
        }, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.imagestream_menu, menu);
        galleryMenuItem = menu.findItem(R.id.image_stream_system_gallery);
        presenter.initMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;

        } else if (item.getItemId() == R.id.image_stream_system_gallery) {
            openGallery();
            return true;

        } else {
            return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomSheetBehavior != null) {
            outState.putParcelable(VIEW_STATE, new ImageStreamMvp.ViewState(bottomSheetBehavior.getState()));
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.no_change, R.anim.slide_out);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.no_change, R.anim.slide_out);
    }

    @Override
    public void initUiComponents() {
        initToolbar();
        initBottomSheet();
    }

    @Override
    public boolean isPermissionGranted(String permission) {
        return PermissionUtil.isPermissionGranted(this, permission);
    }

    @Override
    public void askForPermission(String permission) {
        final String[] permissions = {permission};
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void showImageStream(List<Uri> images, List<MediaResult> selectedImages, boolean showCamera) {
        final ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        layoutParams.height = MATCH_PARENT;
        bottomSheet.setLayoutParams(layoutParams);

        int columns = getResources().getBoolean(R.bool.bottom_sheet_portrait) ? 2 : 3;

        final ImageStreamAdapter adapter = new ImageStreamAdapter(dataSource);
        final StaggeredGridLayoutManager staggeredGridLayoutManager =
                new StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL);

        dataSource.initializeWithImages(ImageStreamItems.fromUris(images, this, getApplicationContext()));

        final List<Uri> selectedUris = new ArrayList<>();
        for(MediaResult mediaResult : selectedImages) {
            selectedUris.add(mediaResult.getOriginalUri());
        }
        dataSource.setItemsSelected(selectedUris);

        if(showCamera){
            dataSource.addStaticItem(ImageStreamItems.forCameraSquare(this));
        }

        // https://code.google.com/p/android/issues/detail?id=230295
        //staggeredGridLayoutManager.setItemPrefetchEnabled(false);
        //staggeredGridLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        initRecycler(adapter, staggeredGridLayoutManager);
    }

    @Override
    public void showList(MediaIntent cameraIntent, MediaIntent documentIntent) {
        final ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        layoutParams.height = WRAP_CONTENT;
        bottomSheet.setLayoutParams(layoutParams);

        final ImageStreamAdapter adapter = new ImageStreamAdapter(dataSource);

        dataSource.addStaticItem(ImageStreamItems.forCameraList(this));
        dataSource.addStaticItem(ImageStreamItems.forDocumentList(this));

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        initRecycler(adapter, linearLayoutManager);
    }

    @Override
    public void showDocumentMenuItem(boolean visible) {
        if (galleryMenuItem != null) {
            galleryMenuItem.setVisible(visible);

        }
        if (floatingActionMenu != null) {
            floatingActionMenu.addMenuItem(R.drawable.ic_file, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presenter.openGallery();
                }
            });
        }
    }

    @Override
    public void showGooglePhotosMenuItem(boolean visible) {
        if (floatingActionMenu != null) {
            floatingActionMenu.addMenuItem(R.drawable.ic_collections, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    presenter.openGooglePhotos();
                }
            });
        }
    }

    @Override
    public void openMediaIntent(MediaIntent mediaIntent) {
        mediaIntent.open(this);
    }

    @Override
    public void finishWithoutResult() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void finishIfNothingIsLeft() {
        if (imageStreamAdapter == null) {
            finishWithoutResult();
        }
    }

    @Override
    public void hideCameraOption() {
        if (imageStreamAdapter != null) {
            //imageStreamAdapter.hideCameraOption();
        } else {
            finishWithoutResult();
        }
    }

    @Override
    public void imagesSelected(List<Uri> uris) {
        final List<MediaResult> mediaResults = new ArrayList<>(uris.size());
        for(Uri uri : uris) {
            mediaResults.add(new MediaResult(null, uri, null, null, null)); // FIXME: temp workaround, this will go away
        }
        finishWithResult(mediaResults);
    }

    @Override
    public void openCamera() {
        presenter.openCamera();
    }

    @Override
    public void openGallery() {
        presenter.openGallery();
    }

    @Override
    public void updateList() {
        imageStreamAdapter.notifyDataSetChanged();
    }

    @Override
    public void setSelected(Uri uri) {
        for(int i = 0, c = dataSource.getItemCount(); i < c; i++) {
            if(dataSource.getItemForPos(i) instanceof ImageStreamItems.StreamItemImage) {
                if(((ImageStreamItems.StreamItemImage)dataSource.getItemForPos(i)).getUri().equals(uri)){
                    imageStreamAdapter.notifyItemChanged(i);
                }
            }
        }
    }

    private void initRecycler(ImageStreamAdapter adapter, RecyclerView.LayoutManager layoutManager) {
        this.imageStreamAdapter = adapter;
        imageList.setItemAnimator(null);
        imageList.setHasFixedSize(true);
        imageList.setItemViewCacheSize(25);
        imageList.setDrawingCacheEnabled(true);
        imageList.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        imageList.setAdapter(adapter);
        imageList.setLayoutManager(layoutManager);
    }

    private void finishWithResult(List<MediaResult> belvedereResults) {
        final Intent intent = ImageStream.this.getIntent();
        intent.putParcelableArrayListExtra(MediaSource.INTERNAL_RESULT_KEY, new ArrayList<>(belvedereResults));
        setResult(RESULT_OK, intent);
        finish();
    }

    private void initToolbar() {
        toolbar.setNavigationIcon(R.drawable.belvedere_ic_close);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle("Photo library");
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void bindViews() {
        this.bottomSheet = findViewById(R.id.bottom_sheet);
        this.dismissArea = findViewById(R.id.dismiss_area);
        this.imageList = (RecyclerView) findViewById(R.id.image_list);
        this.toolbar = (Toolbar) findViewById(R.id.image_stream_toolbar);
        this.floatingActionMenu = (FloatingActionMenu) findViewById(R.id.floating_action_menu);
    }

    private void initBottomSheet() {
        bottomSheet.setVisibility(View.VISIBLE);

        ViewCompat.setElevation(imageList, getResources().getDimensionPixelSize(R.dimen.bottom_sheet_elevation));

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        finish();
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                float offset = 0.6f;
                if (slideOffset >= offset) {
                    ViewCompat.setAlpha(toolbar, 1f - (1f - slideOffset) / (1f - offset));
                    Utils.showToolbar(ImageStream.this);
                } else {
                    Utils.hideToolbar(ImageStream.this);
                }
            }
        });

        if (viewState.getBottomSheetState() == BottomSheetBehavior.STATE_EXPANDED) {
            Utils.showToolbar(ImageStream.this);
        } else {
            Utils.hideToolbar(ImageStream.this);
        }

        bottomSheetBehavior.setState(viewState.getBottomSheetState());
        dismissArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bottomSheet.setClickable(true);
    }

}