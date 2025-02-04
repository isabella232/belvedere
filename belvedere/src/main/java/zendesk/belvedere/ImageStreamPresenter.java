package zendesk.belvedere;


import android.view.View;

import java.util.ArrayList;
import java.util.List;

import zendesk.belvedere.ui.R;

class ImageStreamPresenter implements ImageStreamMvp.Presenter {

    private final ImageStreamMvp.Model model;
    private final ImageStreamMvp.View view;
    private final ImageStream imageStreamBackend;

    ImageStreamPresenter(ImageStreamMvp.Model model, ImageStreamMvp.View view, ImageStream imageStreamBackend) {
        this.model = model;
        this.view = view;
        this.imageStreamBackend = imageStreamBackend;
    }

    @Override
    public void init() {
        presentStream();
        initMenu();
        view.updateToolbarTitle(model.getSelectedMediaResults().size());
        view.updateFloatingActionButton(model.getSelectedMediaResults().size());
    }

    @Override
    public void onImageStreamScrolled(int height, int scrollArea, float scrollPosition) {
        if(scrollPosition >= 0) {
            imageStreamBackend.notifyScrollListener(height, scrollArea, scrollPosition);
        }
    }

    @Override
    public void dismiss() {
        // Null out references
        imageStreamBackend.setImageStreamUi(null, null);

        // Reset animation listener
        imageStreamBackend.notifyScrollListener(0,0,0);

        // notify observers
        imageStreamBackend.notifyDismissed();
    }

    @Override
    public void sendSelectedImages() {
        List<MediaResult> results = model.getSelectedMediaResults();
        imageStreamBackend.notifyImagesSent(results);
    }

    private List<MediaResult> setItemSelected(MediaResult mediaResult, boolean isSelected) {
        final List<MediaResult> mediaResults;

        if(isSelected) {
            mediaResults = model.addToSelectedItems(mediaResult);
        } else{
            mediaResults = model.removeFromSelectedItems(mediaResult);
        }

        return mediaResults;
    }

    private void initMenu() {
        if(model.hasGooglePhotosIntent()) {
            final View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ImageStreamPresenter.this.view.openMediaIntent(model.getGooglePhotosIntent(), imageStreamBackend);
                }
            };

            view.showGooglePhotosMenuItem(clickListener);
        }

        if(model.hasDocumentIntent()) {
            final View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ImageStreamPresenter.this.view.openMediaIntent(model.getDocumentIntent(), imageStreamBackend);
                }
            };

            view.showDocumentMenuItem(clickListener);
        }
    }

    private void presentStream() {

        // Check if we can show the picker above the keyboard
        final boolean fullScreenOnly = model.showFullScreenOnly() || view.shouldShowFullScreen();

        // Init the ui
        view.initViews(fullScreenOnly);

        // Load recent images
        final List<MediaResult> latestImages = model.getLatestImages();

        // Load selected images
        final List<MediaResult> selectedImages = model.getSelectedMediaResults();

        // Populate image stream
        view.showImageStream(latestImages, selectedImages, fullScreenOnly, model.hasCameraIntent(), imageStreamListener);

        // Notify observers
        imageStreamBackend.notifyVisible();
    }

    private final ImageStreamAdapter.Listener imageStreamListener = new ImageStreamAdapter.Listener() {
        @Override
        public void onOpenCamera() {
            if (model.hasCameraIntent()) {
                view.openMediaIntent(model.getCameraIntent(), imageStreamBackend);
            }
        }

        @Override
        public boolean onSelectionChanged(ImageStreamItems.Item item) {
            MediaResult media = item.getMediaResult();
            final long maxFileSize = model.getMaxFileSize();
            final boolean changeSelection;

            if(media != null && media.getSize() <= maxFileSize || maxFileSize == -1L) {
                item.setSelected(!item.isSelected());
                changeSelection = true;

                List<MediaResult> items = setItemSelected(media, item.isSelected());

                view.updateToolbarTitle(items.size());
                view.updateFloatingActionButton(items.size());

                List<MediaResult> results = new ArrayList<>();
                results.add(media);

                if(item.isSelected()) {
                    imageStreamBackend.notifyImageSelected(results);
                } else {
                    imageStreamBackend.notifyImageDeselected(results);
                }

            } else {
                changeSelection = false;
                view.showToast(R.string.belvedere_image_stream_file_too_large);
            }

            return changeSelection;
        }
    };
}