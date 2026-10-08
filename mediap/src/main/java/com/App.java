package com;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.application.Application;
import javafx.animation.AnimationTimer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // ========================= MEDIA =========================
    private MediaPlayer mediaPlayer;
    private MediaView mediaView;
    private StackPane playerArea;
    private Canvas audioCanvas;
    private GraphicsContext audioGraphics;
    private double[] spectrumData = new double[64];
    private AnimationTimer audioVisualizerTimer;
    private boolean currentMediaIsVideo = false;
    private final ObservableList<File> playlistTracks = FXCollections.observableArrayList();
    private final ObservableList<File> filteredTracks = FXCollections.observableArrayList();
    private int currentTrackIndex = -1;

    // ========================= UI ============================
    private Label trackTitleLabel;
    private Label artistLabel;
    private Label timeCurrentLabel;
    private Label timeTotalLabel;
    private Label statusLabel;
    private Label currentMediaTitle;
    private Label currentMediaArtist;
    private Slider progressSlider;
    private Slider volumeSlider;
    private ListView<File> playlistView;
    private TextField searchField;
    private Button playPauseButton;
    private Button muteButton;
    private VBox leftSidebar;
    private VBox rightSidebar;
    private VBox branding;
    private StackPane searchContainer;
    private HBox header;
    private Stage primaryStage;
    private File loadedLibraryRoot;

    private boolean sliderChanging = false;
    private boolean updatingSearchResults = false;
    private boolean muted = false;
    private double previousVolume = 0.65;

    // ========================= COLORS ========================
    private static final String BG = "#090D13";
    private static final String PANEL = "#0D121A";
    private static final String PANEL_2 = "#11161E";
    private static final String CARD = "#1A2029";
    private static final String TEXT = "#F5F7FA";
    private static final String MUTED = "#9299A6";
    private static final String ORANGE = "#FF7A00";
    private static final String ORANGE_DARK = "#3A2111";

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("MELODY - Media Player");
        primaryStage.setWidth(1366);
        primaryStage.setHeight(768);
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(600);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG + ";");

        header = createHeader(primaryStage);
        leftSidebar = createLeftSidebar();
        VBox center = createCenterContent();
        rightSidebar = createRightSidebar(primaryStage);

        root.setTop(header);
        root.setLeft(leftSidebar);
        root.setCenter(center);
        root.setRight(rightSidebar);

        Scene scene = new Scene(root, 1366, 768);
        installKeyboardControls(scene);

        scene.widthProperty().addListener((obs, oldWidth, newWidth) ->
                updateResponsiveLayout(newWidth.doubleValue()));
        scene.heightProperty().addListener((obs, oldHeight, newHeight) ->
                updateResponsiveLayout(scene.getWidth()));

        primaryStage.setScene(scene);
        primaryStage.show();
        updateResponsiveLayout(scene.getWidth());

        primaryStage.setOnCloseRequest(e -> disposeCurrentPlayer());
    }

    // ========================= RESPONSIVE ====================
    private void updateResponsiveLayout(double width) {
        if (leftSidebar == null || rightSidebar == null) return;

        double leftWidth = Math.max(150, Math.min(235, width * 0.155));
        double rightWidth = Math.max(210, Math.min(340, width * 0.225));

        leftSidebar.setMinWidth(leftWidth);
        leftSidebar.setPrefWidth(leftWidth);
        leftSidebar.setMaxWidth(leftWidth);

        rightSidebar.setMinWidth(rightWidth);
        rightSidebar.setPrefWidth(rightWidth);
        rightSidebar.setMaxWidth(rightWidth);
        rightSidebar.setVisible(true);
        rightSidebar.setManaged(true);

        boolean compact = width < 1120;
        boolean veryCompact = width < 900;

        // Reduce sidebar padding as the window gets smaller.
        double leftHorizontalPadding = veryCompact ? 7 : compact ? 9 : 12;
        double leftVerticalPadding = veryCompact ? 10 : compact ? 14 : 18;
        leftSidebar.setPadding(new Insets(
                leftVerticalPadding,
                leftHorizontalPadding,
                veryCompact ? 10 : 16,
                leftHorizontalPadding));

        double rightHorizontalPadding = veryCompact ? 8 : compact ? 12 : 18;
        rightSidebar.setPadding(new Insets(
                veryCompact ? 12 : 20,
                rightHorizontalPadding,
                15,
                rightHorizontalPadding));

        // Keep the right playlist proportional to the sidebar width.
        if (playlistView != null) {
            playlistView.setPrefWidth(rightWidth - (rightHorizontalPadding * 2));
        }

        if (branding != null) {
            branding.setPrefWidth(Math.max(165, Math.min(235, width * 0.17)));
        }

        if (searchContainer != null) {
            double searchWidth = Math.max(220, Math.min(460, width * 0.36));
            searchContainer.setPrefWidth(searchWidth);
            searchContainer.setMaxWidth(Math.max(250, Math.min(560, width * 0.42)));
        }

        if (header != null) {
            header.setSpacing(veryCompact ? 6 : compact ? 10 : 18);
            header.setPadding(new Insets(
                    10, compact ? 12 : 22,
                    10, compact ? 12 : 22));
        }
    }

    // ========================= HEADER ========================
    private HBox createHeader(Stage stage) {
        HBox bar = new HBox(18);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 22, 10, 22));
        bar.setPrefHeight(70);
        bar.setMinHeight(62);
        bar.setStyle("-fx-background-color:#0B1017;" +
                     "-fx-border-color:#f3273f;" +
                     "-fx-border-width:0 0 1 0;");

        branding = new VBox();
branding.setAlignment(Pos.CENTER_LEFT);
branding.setPrefWidth(235);
branding.setMinWidth(165);

Image logoImage = new Image(
        getClass().getResource("/com/images/logo.png.png").toExternalForm()
);

ImageView logoView = new ImageView(logoImage);

logoView.setPreserveRatio(true);
logoView.setFitHeight(52);
logoView.setFitWidth(220);

branding.getChildren().add(logoView);

        searchContainer = new StackPane();
        searchContainer.setPrefWidth(460);
        searchContainer.setMaxWidth(560);
        searchContainer.setPadding(new Insets(0,20,0,140));
        HBox.setHgrow(searchContainer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Search songs, videos, or folders...");
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchField.textProperty().addListener((obs, oldText, newText) -> refreshPlaylistFilter());
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                searchField.requestFocus();
            }
        });
        searchField.setStyle("-fx-background-color:#1A2029;" +
                "-fx-text-fill:#F5F7FA;" +
                "-fx-prompt-text-fill:#747D8B;" +
                "-fx-background-radius:28;" +
                "-fx-border-radius:28;" +
                "-fx-border-color:#303946;" +
                "-fx-padding:11 20 11 44;" +
                "-fx-font-size:14px;");
        searchContainer.getChildren().add(searchField);

        Label searchIcon = new Label("⌕");
        searchIcon.setTextFill(Color.web(TEXT));
        searchIcon.setFont(Font.font("System", 24));
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 14));
        searchContainer.getChildren().add(searchIcon);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button notification = headerButton("♧");
        notification.setMinSize(50, 50);
        notification.setMaxSize(70, 70);

        StackPane avatar = new StackPane();
        avatar.setMinSize(40, 40);
        avatar.setMaxSize(40, 40);
        Circle circle = new Circle(20, Color.web("#2360d1"));
        Label user = new Label("U");
        user.setTextFill(Color.WHITE);
        user.setFont(Font.font("System", FontWeight.BOLD, 13));
        avatar.getChildren().addAll(circle, user);

        bar.getChildren().addAll(branding, searchContainer, spacer, notification, avatar);

        return bar;
    }

    // ========================= LEFT ==========================
    private VBox createLeftSidebar() {
        VBox sidebar = new VBox(5);
            sidebar.setPrefWidth(205);
            sidebar.setMinWidth(190);
            sidebar.setPadding(new Insets(18, 12, 16, 12));
            sidebar.setStyle("-fx-background-color:" + PANEL + ";" +
                "-fx-border-color:#2460d1;" +
                "-fx-border-width:0 1 0 0;");

        Label library = sectionLabel("LIBRARY");
        Button home = sidebarButton("⌂", "Home", true);
        Button music = sidebarButton("♫", "Music", false);
        Button videos = sidebarButton("▣", "Videos", false);
        Button playlists = sidebarButton("☷", "Playlists", false);


        // Load the user's real Windows Music and Videos folders directly.
        music.setOnAction(e -> loadDeviceLibrary("Music"));
        videos.setOnAction(e -> loadDeviceLibrary("Videos"));
        home.setOnAction(e -> {loadedLibraryRoot = null;
            statusLabel.setText("Home — choose Music or Videos to load device media");
        });

        Separator separator = new Separator();
        separator.setStyle("-fx-background-color:#252D38;");

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("MY PLAYLISTS");
        title.setTextFill(Color.web(MUTED));
        title.setFont(Font.font("System", FontWeight.BOLD, 10));

        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Label plus = new Label("+");
        plus.setTextFill(Color.WHITE);
        plus.setFont(Font.font("System", 24));

        titleRow.getChildren().addAll(title, push, plus);

        VBox items = new VBox(3,
                playlistItem("", "Country music", "44 songs"),
                playlistItem("", "Sesotho Fashioneng", "27 songs"),
                playlistItem("", "Litshepe Lr", "16 songs"),
                playlistItem("", "Ntate Stunna", "40 videos"),
                playlistItem("", "Economic Freedom Fighters", "83 items"));

        Region bottom = new Region();
        VBox.setVgrow(bottom, Priority.ALWAYS);
        Label footer = new Label("Your media. Your world.");
        footer.setTextFill(Color.web(MUTED));
        footer.setFont(Font.font("System", 14));
        footer.setMaxWidth(Double.MAX_VALUE);
        footer.setAlignment(Pos.CENTER);

        sidebar.getChildren().addAll(library, home, music, videos, playlists,
                separator, titleRow, items, bottom, footer);
                
        return sidebar;
    }

    // ========================= CENTER ========================
    private VBox createCenterContent() {
        VBox center = new VBox();
        center.setMinWidth(0);
        center.setStyle("-fx-background-color:" + BG + ";");

    // Visual area only. Controls live below Current Media, Spotify-style.
        StackPane player = createPlayerArea();
        VBox.setVgrow(player, Priority.ALWAYS);

        HBox trackInfo = createTrackInfo();
        trackInfo.setMinHeight(76);
        trackInfo.setPrefHeight(76);

        VBox playbackDock = createPlayerControls();
        playbackDock.setMinHeight(132);
        playbackDock.setPrefHeight(150);
        playbackDock.setMaxHeight(170);

        VBox bottom = createBottomPlayerArea();
        bottom.setMinHeight(105);
        bottom.setPrefHeight(105);

        center.getChildren().addAll(player, trackInfo, playbackDock, bottom);
        return center;
    }

    // ========================= PLAYER ========================
    private StackPane createPlayerArea() {
        playerArea = new StackPane();
        playerArea.setMinWidth(0);
        playerArea.setMinHeight(330);
        playerArea.setStyle("-fx-background-color:#05070A;");

        // Real video surface. It is shown only when a video is loaded.
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.setSmooth(true);

        mediaView.fitWidthProperty().bind(playerArea.widthProperty().subtract(20));
        mediaView.fitHeightProperty().bind(playerArea.heightProperty().subtract(20));
        
        StackPane.setAlignment(mediaView, Pos.CENTER);
        mediaView.setVisible(false);
        playerArea.getChildren().add(mediaView);

        // Audio visualization occupies the same visual area as the video.
        audioCanvas = new Canvas();
        audioCanvas.widthProperty().bind(playerArea.widthProperty());
        audioCanvas.heightProperty().bind(playerArea.heightProperty());
        audioGraphics = audioCanvas.getGraphicsContext2D();
        audioCanvas.setVisible(false);
        StackPane.setAlignment(audioCanvas, Pos.CENTER);
        playerArea.getChildren().add(audioCanvas);

        Region overlay = new Region();
        overlay.mouseTransparentProperty().set(true);
        overlay.setStyle("-fx-background-color: linear-gradient(to bottom," +
                "rgba(134, 240, 13, 0.1),transparent 55%,rgba(241, 238, 12, 0.1));");
        playerArea.getChildren().add(overlay);

        VBox topInfo = new VBox(3);
        topInfo.setPadding(new Insets(15, 30, 0, 30));

        trackTitleLabel = new Label("No Media Loaded");
        trackTitleLabel.setTextFill(Color.WHITE);
        trackTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        trackTitleLabel.setWrapText(true);

        artistLabel = new Label("Add a song or video");
        artistLabel.setTextFill(Color.WHITE);
        artistLabel.setFont(Font.font("System", 14));

        topInfo.getChildren().addAll(trackTitleLabel, artistLabel);

        StackPane.setAlignment(topInfo, Pos.TOP_LEFT);
        playerArea.getChildren().add(topInfo);

        startAudioVisualizer();
        return playerArea;
    }

    // ========================= AUDIO VISUALIZER =============
    private void startAudioVisualizer() {
        if (audioVisualizerTimer != null) {
            audioVisualizerTimer.stop();
        }

        audioVisualizerTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawAudioVisualizer();
            }
        };
        audioVisualizerTimer.start();
    }

    private void drawAudioVisualizer() {
        if (audioCanvas == null || audioGraphics == null) return;

        double w = audioCanvas.getWidth();
        double h = audioCanvas.getHeight();
        if (w <= 1 || h <= 1) return;

        GraphicsContext g = audioGraphics;

        // Dark audio-player background.
        g.setFill(Color.web("#060808c2"));
        g.fillRect(0, 0, w, h);

        if (!currentMediaIsVideo && mediaPlayer != null) {
            double centerY = h * 0.58;
            int bars = Math.min(64, spectrumData.length);
            double gap = Math.max(3, w / 420.0);
            double barWidth = Math.max(1, (w - gap * (bars + 1)) / bars);

            for (int i = 0; i < bars; i++) {
                double magnitude = spectrumData[i];
                // JavaFX audio spectrum magnitudes are normally in dB (-60..0).
                double level = Math.max(0, Math.min(1, (magnitude + 60.0) / 60.0));

                // Give very quiet audio a subtle baseline animation.
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    level = Math.max(level, 0.035);
                }

                double barHeight = 12 + level * (h * 0.38);
                double x = gap + i * (barWidth + gap);

                g.setFill(Color.web(i % 3 == 0 ? ORANGE : "#FF9A3D"));
                g.fillRoundRect(x, centerY - barHeight, barWidth, barHeight, 4, 4);

                // Mirror below the center line for a stronger music-player visual.
                g.setGlobalAlpha(0.35);
                g.fillRoundRect(x, centerY + 3, barWidth, barHeight * 0.48, 4, 4);
                g.setGlobalAlpha(1.0);
            }

            g.setStroke(Color.web("#FF7A00"));
            g.setLineWidth(1.2);
            g.setGlobalAlpha(0.55);
            g.strokeLine(0, centerY + 1, w, centerY + 1);
            g.setGlobalAlpha(1.0);

            // Small visualizer caption.
            g.setFill(Color.web("#9299A6"));
            g.setFont(javafx.scene.text.Font.font("System", 12));
            g.fillText("AUDIO VISUALIZER", 28, h - 72);
        }
    }

    private void showAudioVisualization() {
        currentMediaIsVideo = false;
        if (mediaView != null) {
            mediaView.setVisible(false);
            mediaView.setMediaPlayer(mediaPlayer);
        }
        if (audioCanvas != null) audioCanvas.setVisible(true);
    }

    private void showVideoVisualization() {
        currentMediaIsVideo = true;
        if (audioCanvas != null) audioCanvas.setVisible(false);
        if (mediaView != null) mediaView.setVisible(true);
    }

    private VBox createPlayerControls() {
        VBox controls = new VBox(7);
        controls.setPadding(new Insets(10, 28, 0, 28));
        controls.setMaxWidth(Double.MAX_VALUE);
        controls.setMouseTransparent(false);

        HBox timeRow = new HBox(8);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        timeCurrentLabel = new Label("0:00");
        timeTotalLabel = new Label("0:00");
        timeCurrentLabel.setTextFill(Color.WHITE);
        timeTotalLabel.setTextFill(Color.WHITE);

        progressSlider = new Slider(0, 1, 0);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressSlider, Priority.ALWAYS);
        progressSlider.setStyle("-fx-accent:" + ORANGE + ";");
        progressSlider.setOnMousePressed(e -> sliderChanging = true);
        progressSlider.setOnMouseReleased(e -> {
            sliderChanging = false;
            seekToSliderPosition();
        });
        timeRow.getChildren().addAll(timeCurrentLabel, progressSlider, timeTotalLabel);

        HBox buttons = new HBox(18);
        buttons.setAlignment(Pos.CENTER);
        Button shuffle = playerButton("⤨");
        Button previous = playerButton("|◀");
        playPauseButton = playButton();
        Button next = playerButton("▶|");
        Button repeat = playerButton("↻");
        
        HBox quality = new HBox(8,smallPill("1080p"), smallPill("HD"), smallPill("⛶"));
        Button fullscreen = (Button) quality.getChildren().get(2);
        fullscreen.setOnAction(e -> toggleFullscreen());
        quality.setPadding(new Insets(15, 50, 0, 0));
        StackPane.setAlignment(quality, Pos.TOP_LEFT);


        previous.setOnAction(e -> playPreviousTrack());
        next.setOnAction(e -> playNextTrack());
        playPauseButton.setOnAction(e -> togglePlayPause());
        shuffle.setOnAction(e -> statusLabel.setText("Shuffle selected"));
        repeat.setOnAction(e -> statusLabel.setText("Repeat selected"));

        HBox bottom = new HBox(7);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(15, 0, 0, 50));
        statusLabel = new Label("Ready — add a song or video to play");
        statusLabel.setTextFill(Color.web(MUTED));
        statusLabel.setFont(Font.font("System", 11));
       

        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        bottom.getChildren().add(push);

        muteButton = new Button("🔊");
        muteButton.setStyle("-fx-background-color:transparent;-fx-text-fill:white;-fx-font-size:17px;");
        muteButton.setOnAction(e -> toggleMute());
        volumeSlider = new Slider(0, 1, previousVolume);
        volumeSlider.setPrefWidth(115);
        volumeSlider.setMaxWidth(150);
        volumeSlider.setStyle("-fx-accent:" + ORANGE + ";");
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(newVal.doubleValue());
            if (newVal.doubleValue() > 0) {
                muted = false;
                muteButton.setText("🔊");
            }
        });
        bottom.getChildren().addAll(muteButton, volumeSlider);

        buttons.getChildren().addAll(quality, shuffle, previous, playPauseButton, next, repeat, bottom);

        controls.getChildren().addAll(timeRow, buttons);
        return controls;
    }

    // ========================= TRACK INFO ====================
    private HBox createTrackInfo() {
        HBox info = new HBox(16);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setPadding(new Insets(10, 18, 10, 18));
        info.setStyle("-fx-background-color:" + PANEL_2 + ";" +
                "-fx-border-color:#202733;" +
                "-fx-border-width:1 0 1 0;");

        StackPane album = new StackPane();
        album.setMinSize(58, 58);
        album.setMaxSize(58, 58);
        album.setStyle("-fx-background-color:#242A33;-fx-background-radius:7;");
        Label icon = new Label("♫");
        icon.setTextFill(Color.web(ORANGE));
        icon.setFont(Font.font("System", 28));
        album.getChildren().add(icon);

        VBox details = new VBox(2);
        currentMediaTitle = new Label("Current Media");
        currentMediaTitle.setTextFill(Color.WHITE);
        currentMediaTitle.setFont(Font.font("System", FontWeight.BOLD, 17));
        currentMediaArtist = new Label("MELODY Media Player");
        currentMediaArtist.setTextFill(Color.web(MUTED));
        currentMediaArtist.setFont(Font.font("System", 12));
        details.getChildren().addAll(currentMediaTitle, currentMediaArtist);

        Region push = new Region();
        HBox.setHgrow(push, Priority.ALWAYS);
        Button like = smallIconButton("♥");
        like.setTextFill(Color.web(ORANGE));
        Button more = smallIconButton("•••");
        info.getChildren().addAll(album, details, push, like, more);
        return info;
    }

    // ========================= BOTTOM ========================
    private VBox createBottomPlayerArea() {
        VBox bottom = new VBox(5);
        bottom.setPadding(new Insets(8, 18, 10, 18));
        bottom.setStyle("-fx-background-color:" + PANEL + ";");

        HBox tabs = new HBox(30);
        Label queue = new Label("Queue");
        queue.setTextFill(Color.WHITE);
        queue.setFont(Font.font("System", FontWeight.BOLD, 16));
        Label lyrics = new Label("Lyrics");
        lyrics.setTextFill(Color.web(MUTED));
        lyrics.setFont(Font.font("System", 16));
        tabs.getChildren().addAll(queue, lyrics);

        HBox visualizer = new HBox(3);
        visualizer.setAlignment(Pos.BOTTOM_LEFT);
        for (int i = 0; i < 80; i++) {
            Region bar = new Region();
            double height = 3 + ((i * 17) % 30);
            bar.setPrefWidth(3);
            bar.setPrefHeight(height);
            bar.setStyle("-fx-background-color:" + (i % 4 == 0 ? ORANGE : "#3C4654") + ";");
            visualizer.getChildren().add(bar);
        }
        HBox.setHgrow(visualizer, Priority.ALWAYS);
        bottom.getChildren().addAll(tabs, visualizer);
        return bottom;
    }

    // ========================= RIGHT =========================
    private VBox createRightSidebar(Stage stage) {
        VBox right = new VBox(10);
        right.setPrefWidth(320);
        right.setMinWidth(245);
        right.setPadding(new Insets(20, 18, 15, 18));
        right.setStyle("-fx-background-color:" + PANEL + ";" +
                "-fx-border-color:#202733;" +
                "-fx-border-width:0 0 0 1;");

        HBox tabs = new HBox(0);
        Button upNext = new Button("Playlist");
        Button liked = new Button("Liked Songs");
        HBox.setHgrow(upNext, Priority.ALWAYS);
        HBox.setHgrow(liked, Priority.ALWAYS);
        upNext.setMaxWidth(Double.MAX_VALUE);
        liked.setMaxWidth(Double.MAX_VALUE);
        upNext.setStyle("-fx-background-color:" + ORANGE + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-background-radius:22 0 0 22;-fx-padding:11;");
        liked.setStyle("-fx-background-color:#151B24;-fx-text-fill:#D8DCE3;-fx-background-radius:0 22 22 0;-fx-padding:11;");
        tabs.getChildren().addAll(upNext, liked);

        playlistView = new ListView<>(playlistTracks);
        playlistView.setMinHeight(120);
        playlistView.setStyle("-fx-background-color:#0A0E14;-fx-control-inner-background:#0A0E14;-fx-border-color:transparent;");
        VBox.setVgrow(playlistView, Priority.ALWAYS);
        playlistView.setCellFactory(v -> new ListCell<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (empty || file == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color:transparent;");
                    return;
                }
                boolean folder = file.isDirectory();
                Label icon = new Label(folder ? "📁" : (isVideo(file) ? "▶" : "♫"));
                icon.setTextFill(Color.web(folder ? "#F6C453" : ORANGE));
                icon.setFont(Font.font("System", 18));

                VBox text = new VBox(2);
                Label title = new Label(folder ? file.getName() : stripExtension(file.getName()));
                title.setTextFill(Color.WHITE);
                title.setFont(Font.font("System", FontWeight.BOLD, 13));
                title.setWrapText(true);

                Label type = new Label(folder ? "Folder" : (isVideo(file) ? "Video" : "Audio"));
                type.setTextFill(Color.web(MUTED));
                type.setFont(Font.font("System", 11));

                text.getChildren().addAll(title, type);

                Region push = new Region();
                HBox.setHgrow(push, Priority.ALWAYS);

                HBox row = new HBox(10, icon, text, push);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(9, 7, 9, 7));

                setGraphic(row);
                setText(null);
                setStyle("-fx-background-color:transparent;");
            }
        });

        playlistView.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            if (newFile != null) {
                currentTrackIndex = playlistTracks.indexOf(newFile);
                if (newFile.isDirectory()) {
                    statusLabel.setText("Folder: " + newFile.getAbsolutePath());
                    return;
                }
                playMedia(newFile);
            }
        });

        Button add = new Button("+  Add Media");
        Button remove = new Button("Remove");

        add.setMaxWidth(Double.MAX_VALUE);
        remove.setMaxWidth(Double.MAX_VALUE);

        add.setStyle("-fx-background-color:" + ORANGE + ";-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:11;-fx-background-radius:7;");
        remove.setStyle("-fx-background-color:#171D26;-fx-text-fill:#E5E8ED;-fx-padding:11;-fx-background-radius:7;");

        add.setOnAction(e -> addMediaFiles(stage));
        remove.setOnAction(e -> removeSelectedTrack());

        Label keyboard = new Label("SPACE = Play/Pause   •   S = Stop   •   N = Next   •   P = Previous   •   M = Mute");
        keyboard.setTextFill(Color.web(MUTED));
        keyboard.setWrapText(true);
        keyboard.setFont(Font.font("System", 14));

        right.getChildren().addAll(tabs, playlistView, add, remove, keyboard);
        return right;
    }

    // ========================= DEVICE LIBRARY =================
    private void loadDeviceLibrary(String libraryName) {
        File root = new File(System.getProperty("user.home"), libraryName);

        if (!root.exists() || !root.isDirectory()) {
            statusLabel.setText("The Windows " + libraryName + " folder was not found: " + root.getAbsolutePath());
            return;
        }

        loadedLibraryRoot = root;
        disposeCurrentPlayer();
        clearCurrentMedia();
        playlistTracks.clear();
        currentTrackIndex = -1;

        scanLibraryRecursively(root, new HashSet<String>());

        if (playlistTracks.isEmpty()) {
            statusLabel.setText("No supported media found in " + root.getAbsolutePath());
            return;
        }

        int firstPlayable = findNextPlayableIndex(-1);
        if (firstPlayable >= 0) {
            currentTrackIndex = firstPlayable;
            playlistView.getSelectionModel().select(firstPlayable);
            playlistView.scrollTo(firstPlayable);
        }

        statusLabel.setText("Loaded " + playlistTracks.size() + " items from " + libraryName + " — " + root.getAbsolutePath());
    }

    private void scanLibraryRecursively(File folder, Set<String> visited) {
        if (folder == null || !folder.isDirectory()) return;

        String canonical;
        try {
            canonical = folder.getCanonicalPath();
        } catch (IOException ex) {
            canonical = folder.getAbsolutePath();
        }
        if (!visited.add(canonical.toLowerCase())) return;

        File[] children = folder.listFiles();
        if (children == null) return;

        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, children);
        Collections.sort(sorted, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File child : sorted) {
            if (child.isDirectory()) {
                // Show the folder in the right panel, then scan everything inside it.
                if (!containsPath(child)) playlistTracks.add(child);
                scanLibraryRecursively(child, visited);
            } else if (isSupportedMedia(child) && !containsPath(child)) {
                playlistTracks.add(child);
            }
        }
    }

    private boolean isSupportedMedia(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") ||
               name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".aiff") ||
               name.endsWith(".aif") || name.endsWith(".mp4") || name.endsWith(".m4v") ||
               name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".webm") ||
               name.endsWith(".mkv");
    }

    // ========================= FILES =========================
    private void addMediaFiles(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Music or Video");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Media Files",
                "*.mp3", "*.wav", "*.m4a", "*.aac", "*.mp4", "*.m4v", "*.mov", "*.avi", "*.webm"));

        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        int first = playlistTracks.size();
        for (File file : files) {
            if (file != null && file.isFile() && isSupportedMedia(file) && !containsPath(file)) {
                playlistTracks.add(file);
            }
        }

        refreshPlaylistFilter();

        if (currentTrackIndex < 0 && !playlistTracks.isEmpty()) {
            currentTrackIndex = findNextPlayableIndex(first - 1);
            if (currentTrackIndex >= 0) {
                playlistView.getSelectionModel().select(currentTrackIndex);
                playlistView.scrollTo(currentTrackIndex);
            }
        }
        statusLabel.setText(playlistTracks.size() + " media item(s) in playlist");
    }

    private void removeSelectedTrack() {
        File selected = playlistView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a media item first");
            return;
        }

        int removed = playlistTracks.indexOf(selected);
        boolean wasCurrent = removed == currentTrackIndex;
        playlistTracks.remove(selected);

        if (playlistTracks.isEmpty()) {
            currentTrackIndex = -1;
            disposeCurrentPlayer();
            clearCurrentMedia();
            statusLabel.setText("Playlist is empty");
            return;
        }

        if (wasCurrent) {
            currentTrackIndex = Math.min(removed, playlistTracks.size() - 1);
            playlistView.getSelectionModel().select(currentTrackIndex);
        } else if (removed < currentTrackIndex) {
            currentTrackIndex--;
        }
        statusLabel.setText("Removed: " + selected.getName());
    }

    // ========================= PLAYBACK ======================
    private void playMedia(File file) {
        if (file == null || !file.exists() || !file.isFile() || !isSupportedMedia(file)) {
            if (file != null && file.isDirectory()) {
                statusLabel.setText("Folder: " + file.getAbsolutePath());
            }
            return;
        }
        disposeCurrentPlayer();

        try {
            boolean video = isVideo(file);
            currentMediaIsVideo = video;

            Media media = new Media(file.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaPlayer.setVolume(volumeSlider == null ? previousVolume : volumeSlider.getValue());

            if (video) {
                showVideoVisualization();
            } else {
                showAudioVisualization();
                spectrumData = new double[64];

                // JavaFX MediaPlayer's real audio spectrum drives the visualizer.
                mediaPlayer.setAudioSpectrumInterval(0.05);
                mediaPlayer.setAudioSpectrumNumBands(64);
                mediaPlayer.setAudioSpectrumThreshold(-60);
                mediaPlayer.setAudioSpectrumListener((timestamp, duration, magnitudes, phases) -> {
                    if (magnitudes == null) return;
                    int count = Math.min(spectrumData.length, magnitudes.length);
                    for (int i = 0; i < count; i++) {
                        spectrumData[i] = magnitudes[i];
                    }
                });
            }

            String title = stripExtension(file.getName());
            trackTitleLabel.setText(title);
            artistLabel.setText(video ? "Local Video" : "Local Audio");
            currentMediaTitle.setText(title);
            currentMediaArtist.setText(video ? "Local Video" : "Local Audio");
            statusLabel.setText("Loading: " + file.getName());

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                timeTotalLabel.setText(formatTime(total));
                progressSlider.setValue(0);
                statusLabel.setText("Playing: " + file.getName());
                mediaPlayer.play();
                updatePlayButton();
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (newTime == null) return;
                timeCurrentLabel.setText(formatTime(newTime));
                Duration total = mediaPlayer.getTotalDuration();
                if (!sliderChanging && total != null && !total.isUnknown() && total.toMillis() > 0) {
                    progressSlider.setValue(Math.max(0, Math.min(1, newTime.toMillis() / total.toMillis())));
                }
            });

            mediaPlayer.setOnEndOfMedia(this::playNextTrack);
            mediaPlayer.setOnError(() -> statusLabel.setText("Unable to play: " + file.getName()));
        } catch (Exception ex) {
            statusLabel.setText("Could not load media: " + file.getName());
            System.err.println("Media error: " + ex.getMessage());
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!playlistTracks.isEmpty()) {
                if (currentTrackIndex < 0) currentTrackIndex = 0;
                playlistView.getSelectionModel().select(currentTrackIndex);
            } else {
                statusLabel.setText("Add a media file first");
            }
            return;
        }

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            statusLabel.setText("Paused");
        } else {
            mediaPlayer.play();
            statusLabel.setText("Playing");
        }
        updatePlayButton();
    }

    private void stopPlayback() {
        if (mediaPlayer == null) return;
        mediaPlayer.stop();
        progressSlider.setValue(0);
        timeCurrentLabel.setText("0:00");
        statusLabel.setText("Stopped");
        updatePlayButton();
    }

    private void playNextTrack() {
        int next = findNextPlayableIndex(currentTrackIndex);
        if (next < 0) return;
        currentTrackIndex = next;
        playlistView.getSelectionModel().select(currentTrackIndex);
        playlistView.scrollTo(currentTrackIndex);
    }

    private void playPreviousTrack() {
        int previous = findPreviousPlayableIndex(currentTrackIndex);
        if (previous < 0) return;
        currentTrackIndex = previous;
        playlistView.getSelectionModel().select(currentTrackIndex);
        playlistView.scrollTo(currentTrackIndex);
    }

    private int findNextPlayableIndex(int fromIndex) {
        if (playlistTracks.isEmpty()) return -1;
        int size = playlistTracks.size();
        for (int step = 1; step <= size; step++) {
            int index = (fromIndex + step + size) % size;
            File file = playlistTracks.get(index);
            if (file.isFile() && isSupportedMedia(file)) return index;
        }
        return -1;
    }

    private int findPreviousPlayableIndex(int fromIndex) {
        if (playlistTracks.isEmpty()) return -1;
        int size = playlistTracks.size();
        int start = fromIndex < 0 ? 0 : fromIndex;
        for (int step = 1; step <= size; step++) {
            int index = (start - step + size * 2) % size;
            File file = playlistTracks.get(index);
            if (file.isFile() && isSupportedMedia(file)) return index;
        }
        return -1;
    }
    // ========================= SEARCH =========================
    private void refreshPlaylistFilter() {
        if (playlistView == null || searchField == null) return;

        String query = searchField.getText();
        query = query == null ? "" : query.trim().toLowerCase();

        updatingSearchResults = true;
        try {
            filteredTracks.clear();

            if (query.isEmpty()) {
                filteredTracks.addAll(playlistTracks);
            } else {
                for (File file : playlistTracks) {
                    if (matchesSearch(file, query)) {
                        filteredTracks.add(file);
                    }
                }
            }

            // Keep the ListView connected to the filtered result.
            if (playlistView.getItems() != filteredTracks) {
                playlistView.setItems(filteredTracks);
            }

            if (filteredTracks.isEmpty()) {
                statusLabel.setText(query.isEmpty()
                        ? "No media loaded"
                        : "No media matches: \"" + query + "\"");
            } else if (!query.isEmpty()) {
                statusLabel.setText("Found " + filteredTracks.size() + " item(s) for: " + query);
            }
        } finally {
            updatingSearchResults = false;
        }
    }

    /**
     * Searches the file/folder name, full path and media type.
     */
    private boolean matchesSearch(File file, String query) {
        if (file == null) return false;

        String name = file.getName().toLowerCase();
        String path = file.getAbsolutePath().toLowerCase();

        if (name.contains(query) || path.contains(query)) return true;

        if (file.isDirectory()) {
            return "folder".contains(query);
        }

        if (isVideo(file)) {
            return "video".contains(query);
        }

        return "audio".contains(query) || "music".contains(query) || "song".contains(query);
    }

    // ========================= VOLUME ========================
    private void adjustVolume(double amount) {
        if (volumeSlider == null) return;
        double value = Math.max(0, Math.min(1, volumeSlider.getValue() + amount));
        volumeSlider.setValue(value);
        muted = value == 0;
        if (muteButton != null) muteButton.setText(muted ? "🔇" : "🔊");
    }

    private void toggleMute() {
        if (volumeSlider == null) return;
        if (!muted && volumeSlider.getValue() > 0) {
            previousVolume = volumeSlider.getValue();
            volumeSlider.setValue(0);
            muted = true;
            muteButton.setText("🔇");
        } else {
            if (previousVolume <= 0) previousVolume = 0.65;
            volumeSlider.setValue(previousVolume);
            muted = false;
            muteButton.setText("🔊");
        }
    }

    private void seekToSliderPosition() {
        if (mediaPlayer == null) return;
        Duration total = mediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown()) return;
        mediaPlayer.seek(total.multiply(progressSlider.getValue()));
    }

    // ========================= KEYBOARD ======================
    private void installKeyboardControls(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.SPACE) {
                togglePlayPause();
            } else if (code == KeyCode.S) {
                stopPlayback();
            } else if (code == KeyCode.N) {
                playNextTrack();
            } else if (code == KeyCode.P) {
                playPreviousTrack();
            } else if (code == KeyCode.UP) {
                adjustVolume(0.05);
            } else if (code == KeyCode.DOWN) {
                adjustVolume(-0.05);
            } else if (code == KeyCode.M) {
                toggleMute();
            } else {
                return;
            }
            event.consume();
        });
    }

    // ========================= UTILITIES =====================
    private void toggleFullscreen() {

    if (mediaView == null || mediaView.getScene() == null) return;

    Stage stage = (Stage) mediaView.getScene().getWindow();

    boolean fullscreen = !stage.isFullScreen();

    if (fullscreen) {
        leftSidebar.setVisible(false);
        leftSidebar.setManaged(false);

        rightSidebar.setVisible(false);
        rightSidebar.setManaged(false);
    } else {
        leftSidebar.setVisible(true);
        leftSidebar.setManaged(true);

        rightSidebar.setVisible(true);
        rightSidebar.setManaged(true);
    }

    stage.setFullScreen(fullscreen);
}

    private void updatePlayButton() {
        if (playPauseButton == null) return;
        boolean playing = mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
        playPauseButton.setText(playing ? "▶" : "Ⅱ");
    }

    private void disposeCurrentPlayer() {
        if (mediaPlayer != null) {
            try { mediaPlayer.setAudioSpectrumListener(null); } catch (Exception ignored) { }
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
            try { mediaPlayer.dispose(); } catch (Exception ignored) { }
            mediaPlayer = null;
        }
        currentMediaIsVideo = false;
        spectrumData = new double[64];
        if (mediaView != null) {
            mediaView.setMediaPlayer(null);
            mediaView.setVisible(false);
        }
        if (audioCanvas != null) audioCanvas.setVisible(true);
    }

    private void clearCurrentMedia() {
        trackTitleLabel.setText("No Media Loaded");
        artistLabel.setText("Add a song or video");
        currentMediaTitle.setText("Current Media");
        currentMediaArtist.setText("VLCify Media Player");
        timeCurrentLabel.setText("0:00");
        timeTotalLabel.setText("0:00");
        progressSlider.setValue(0);
        updatePlayButton();
    }

    private boolean containsPath(File file) {
        String path = file.getAbsolutePath();
        for (File existing : playlistTracks) {
            if (existing.getAbsolutePath().equalsIgnoreCase(path)) return true;
        }
        return false;
    }

    private boolean isVideo(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".m4v") || name.endsWith(".mov") ||
               name.endsWith(".avi") || name.endsWith(".webm");
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) return "0:00";
        int total = (int) Math.floor(duration.toSeconds());
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }

    // ========================= UI HELPERS ====================
    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.web(MUTED));
        label.setFont(Font.font("System", FontWeight.BOLD, 10));
        label.setPadding(new Insets(10, 10, 5, 10));
        return label;
    }

    private Button sidebarButton(String icon, String text, boolean active) {
        Button button = new Button(icon + "   " + text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPadding(new Insets(11, 12, 11, 12));
        button.setStyle(active ?
                "-fx-background-color:" + ORANGE_DARK + ";-fx-text-fill:" + ORANGE + ";-fx-font-size:14px;-fx-font-weight:bold;-fx-background-radius:5;" :
                "-fx-background-color:transparent;-fx-text-fill:#C6CBD4;-fx-font-size:14px;-fx-background-radius:5;");
        if (!active) {
            button.setOnMouseEntered(e -> button.setStyle("-fx-background-color:#151B23;-fx-text-fill:white;-fx-font-size:14px;-fx-background-radius:5;"));
            button.setOnMouseExited(e -> button.setStyle("-fx-background-color:transparent;-fx-text-fill:#C6CBD4;-fx-font-size:14px;-fx-background-radius:5;"));
        }
        return button;
    }

    private VBox playlistItem(String icon, String title, String count) {
        VBox item = new VBox(2);
        item.setPadding(new Insets(6, 10, 6, 10));
        Label top = new Label(icon + "   " + title);
        top.setTextFill(Color.web(TEXT));
        top.setFont(Font.font("System", 13));
        Label bottom = new Label(count);
        bottom.setTextFill(Color.web(MUTED));
        bottom.setFont(Font.font("System", 10));
        item.getChildren().addAll(top, bottom);
        return item;
    }

    private Button headerButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color:transparent;-fx-text-fill:#D9DDE4;-fx-font-size:20px;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color:#181E27;-fx-text-fill:white;-fx-font-size:20px;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color:transparent;-fx-text-fill:#D9DDE4;-fx-font-size:20px;"));
        return button;
    }

    private Button smallPill(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color:rgba(10,15,20,0.78);-fx-border-color:#A7AFBA;-fx-border-radius:6;-fx-background-radius:6;-fx-text-fill:white;-fx-padding:5 9;");
        return button;
    }

    private Button playerButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color:transparent;-fx-text-fill:white;-fx-font-size:20px;-fx-min-width:42px;-fx-min-height:42px;-fx-background-radius:50%;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color:rgba(255,122,0,0.85);-fx-text-fill:white;-fx-font-size:20px;-fx-min-width:42px;-fx-min-height:42px;-fx-background-radius:50%;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color:transparent;-fx-text-fill:white;-fx-font-size:20px;-fx-min-width:42px;-fx-min-height:42px;-fx-background-radius:50%;"));
        return button;
    }

    private Button playButton() {
        Button button = new Button("▶");
        button.setStyle("-fx-background-color:" + ORANGE + ";-fx-text-fill:white;-fx-background-radius:50%;-fx-font-size:23px;-fx-min-width:60px;-fx-min-height:60px;-fx-max-width:60px;-fx-max-height:60px;");
        return button;
    }

    private Button smallIconButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color:transparent;-fx-text-fill:#C8CDD5;-fx-font-size:20px;");
        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
