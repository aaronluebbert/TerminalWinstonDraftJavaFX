package com.example.terminalwinstondraftjavafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

public class WinstonDraftApplication extends Application {

    // ── game state ──────────────────────────────────────────────────────────
    // 1 = pile1 | 2 = pile2 | 3 = pile3
    // 4 = draftPool | 5 = playerOnePool | 6 = playerTwoPool
    private final List<Card> draftPool     = new ArrayList<>();
    private final List<Card> pile1         = new ArrayList<>();
    private final List<Card> pile2         = new ArrayList<>();
    private final List<Card> pile3         = new ArrayList<>();
    private final List<Card> playerOnePool = new ArrayList<>();
    private final List<Card> playerTwoPool = new ArrayList<>();

    private int currentPlayer = 1;
    private int currentPile   = 1;

    // ── UI refs ─────────────────────────────────────────────────────────────
    private Label      statusLabel;
    private Label      cardsRemainingLabel;
    private Button     takeBtn;
    private Button     skipBtn;
    private VBox       pile1Box, pile2Box, pile3Box;
    private BorderPane root;

    private final Map<String, Image> imageCache = new HashMap<>();
    private Image cardBackImage;

    // ── colours ─────────────────────────────────────────────────────────────
    private static final String BG_DARK       = "#1a1a2e";
    private static final String BG_MID        = "#16213e";
    private static final String BG_PANEL      = "#0f3460";
    private static final String ACCENT_GOLD   = "#e8b84b";
    private static final String ACCENT_ACTIVE = "#e94560";
    private static final String TEXT_LIGHT    = "#eaeaea";
    private static final String TEXT_DIM      = "#8899aa";

    // ── entry point ─────────────────────────────────────────────────────────
    @Override
    public void start(Stage stage) {
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        loadCSV();
        initDraftPool();

        cardBackImage = new Image(
                "https://backs.scryfall.io/large/back.jpg",
                180, 250, true, true, true
        );

        root = new BorderPane();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        root.setTop(buildTopBar());
        root.setCenter(buildPilesArea());
        root.setBottom(buildControlBar());

        refreshUI();

        Scene scene = new Scene(root, 1400, 900);
        stage.setTitle("Winston Draft");
        stage.setScene(scene);
        stage.show();
    }

    // ── CSV loading ─────────────────────────────────────────────────────────
    private void loadCSV() {
        List<Card> allCards = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("cardList.csv"))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = splitCSVLine(line);
                if (parts.length < 5) continue;
                String name    = parts[0].trim();
                String setCode = parts[4].trim().toLowerCase();
                allCards.add(new Card(name, setCode));
            }
        } catch (IOException e) {
            System.err.println("Could not read cardList.csv: " + e.getMessage());
        }

        Collections.shuffle(allCards);
        int count = Math.min(90, allCards.size());
        for (int i = 0; i < count; i++) {
            draftPool.add(allCards.get(i));
        }
    }

    private String[] splitCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private void initDraftPool() {
        if (draftPool.size() < 3) {
            System.err.println("Not enough cards in draftPool to start (need at least 3). Check cardList.csv path.");
            return;
        }
        pile1.add(draftPool.remove(0));
        pile2.add(draftPool.remove(0));
        pile3.add(draftPool.remove(0));
    }

    // ── UI builders ─────────────────────────────────────────────────────────
    private HBox buildTopBar() {
        Label title = new Label("WINSTON DRAFT");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 26));
        title.setTextFill(Color.web(ACCENT_GOLD));

        statusLabel = new Label();
        statusLabel.setFont(Font.font("Georgia", FontWeight.BOLD, 17));
        statusLabel.setTextFill(Color.web(ACCENT_ACTIVE));

        cardsRemainingLabel = new Label();
        cardsRemainingLabel.setFont(Font.font("Georgia", 15));
        cardsRemainingLabel.setTextFill(Color.web(TEXT_DIM));

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        HBox bar = new HBox(16, title, spacer1, statusLabel, spacer2, cardsRemainingLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.setStyle(
                "-fx-background-color: " + BG_MID + ";" +
                        "-fx-border-color: " + ACCENT_GOLD + ";" +
                        "-fx-border-width: 0 0 2 0;"
        );
        return bar;
    }

    private HBox buildPilesArea() {
        pile1Box = buildPileColumn();
        pile2Box = buildPileColumn();
        pile3Box = buildPileColumn();

        ScrollPane sp1 = wrapInScroll(pile1Box);
        ScrollPane sp2 = wrapInScroll(pile2Box);
        ScrollPane sp3 = wrapInScroll(pile3Box);

        HBox pilesRow = new HBox(24, sp1, sp2, sp3);
        pilesRow.setAlignment(Pos.TOP_CENTER);
        pilesRow.setPadding(new Insets(24));
        return pilesRow;
    }

    private VBox buildPileColumn() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        box.setMinHeight(600);
        box.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background-radius: 10;");
        return box;
    }

    private ScrollPane wrapInScroll(VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setPrefWidth(440);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    private HBox buildControlBar() {
        takeBtn = new Button("TAKE PILE");
        styleBtn(takeBtn, ACCENT_GOLD, "#1a1a2e");
        takeBtn.setOnAction(e -> handleTake());

        skipBtn = new Button("SKIP PILE");
        styleBtn(skipBtn, ACCENT_ACTIVE, "#ffffff");
        skipBtn.setOnAction(e -> handleSkip());

        HBox bar = new HBox(32, takeBtn, skipBtn);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(20));
        bar.setStyle(
                "-fx-background-color: " + BG_MID + ";" +
                        "-fx-border-color: " + ACCENT_GOLD + ";" +
                        "-fx-border-width: 2 0 0 0;"
        );
        return bar;
    }

    private void styleBtn(Button btn, String bg, String fg) {
        btn.setStyle(
                "-fx-background-color: " + bg + ";" +
                        "-fx-text-fill: " + fg + ";" +
                        "-fx-font-family: Georgia;" +
                        "-fx-font-size: 15px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 10 32 10 32;" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;"
        );
        DropShadow ds = new DropShadow(10, Color.web(bg, 0.6));
        btn.setEffect(ds);
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
    }

    // ── game actions ────────────────────────────────────────────────────────
    private void handleTake() {
        int playerPool = (currentPlayer == 1) ? 5 : 6;
        if (currentPile == 1) {
            while (!pile1.isEmpty()) replaceCard(playerPool, 1);
            replaceCard(1, 4);
        } else if (currentPile == 2) {
            while (!pile2.isEmpty()) replaceCard(playerPool, 2);
            replaceCard(2, 4);
        } else if (currentPile == 3) {
            while (!pile3.isEmpty()) replaceCard(playerPool, 3);
            replaceCard(3, 4);
        }
        advanceTurn();
    }

    private void handleSkip() {
        if (currentPile == 1) {
            replaceCard(1, 4);
        } else if (currentPile == 2) {
            replaceCard(2, 4);
        } else if (currentPile == 3) {
            replaceCard(3, 4);
        }

        if (currentPile < 3) {
            currentPile++;
            refreshUI();
        } else {
            // skipped all three — forced to take top card
            if (!draftPool.isEmpty()) {
                Card forced = draftPool.remove(0);
                if (currentPlayer == 1) {
                    playerOnePool.add(forced);
                } else {
                    playerTwoPool.add(forced);
                }
            }
            advanceTurn();
        }
    }

    private void advanceTurn() {
        if (pile1.isEmpty() && pile2.isEmpty() && pile3.isEmpty()) {
            endDraft();
            return;
        }
        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        currentPile   = 1;
        refreshUI();
    }

    private void endDraft() {
        takeBtn.setDisable(true);
        skipBtn.setDisable(true);
        statusLabel.setText("Draft Complete!");

        try (PrintWriter p1 = new PrintWriter("playerOneDeck.txt");
             PrintWriter p2 = new PrintWriter("playerTwoDeck.txt")) {
            for (Card c : playerOnePool) p1.println("1 " + c.name);
            for (Card c : playerTwoPool) p2.println("1 " + c.name);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Label doneMsg = new Label("Draft complete!\nDecklists saved to playerOneDeck.txt & playerTwoDeck.txt");
        doneMsg.setTextFill(Color.web(ACCENT_GOLD));
        doneMsg.setFont(Font.font("Georgia", FontWeight.BOLD, 18));
        doneMsg.setAlignment(Pos.CENTER);

        VBox center = new VBox(doneMsg);
        center.setAlignment(Pos.CENTER);
        root.setCenter(center);
    }

    // ── replaceCard ──────────────────────────────────────────────────────────
    // 1 = pile1 | 2 = pile2 | 3 = pile3
    // 4 = draftPool | 5 = playerOnePool | 6 = playerTwoPool
    private void replaceCard(int listReceiving, int listRemovingNumber) {
        if (listReceiving == 1) {
            if (!draftPool.isEmpty()) pile1.add(draftPool.remove(0));
        } else if (listReceiving == 2) {
            if (!draftPool.isEmpty()) pile2.add(draftPool.remove(0));
        } else if (listReceiving == 3) {
            if (!draftPool.isEmpty()) pile3.add(draftPool.remove(0));
        } else if (listReceiving == 5) {
            if (listRemovingNumber == 1)      playerOnePool.add(pile1.remove(0));
            else if (listRemovingNumber == 2) playerOnePool.add(pile2.remove(0));
            else if (listRemovingNumber == 3) playerOnePool.add(pile3.remove(0));
        } else if (listReceiving == 6) {
            if (listRemovingNumber == 1)      playerTwoPool.add(pile1.remove(0));
            else if (listRemovingNumber == 2) playerTwoPool.add(pile2.remove(0));
            else if (listRemovingNumber == 3) playerTwoPool.add(pile3.remove(0));
        }
    }

    // ── UI refresh ──────────────────────────────────────────────────────────
    private void refreshUI() {
        statusLabel.setText("Player " + currentPlayer + "  -  Pile " + currentPile);
        cardsRemainingLabel.setText("Cards remaining: " + draftPool.size());

        renderPileColumn(pile1Box, pile1, "Pile 1", currentPile == 1);
        renderPileColumn(pile2Box, pile2, "Pile 2", currentPile == 2);
        renderPileColumn(pile3Box, pile3, "Pile 3", currentPile == 3);

        skipBtn.setDisable(currentPile == 3 && draftPool.isEmpty());
    }

    private void renderPileColumn(VBox box, List<Card> pile, String title, boolean revealed) {
        box.getChildren().clear();

        if (revealed) {
            box.setStyle(
                    "-fx-background-color: #1a3a5c;" +
                            "-fx-background-radius: 10;" +
                            "-fx-border-color: " + ACCENT_GOLD + ";" +
                            "-fx-border-width: 2;" +
                            "-fx-border-radius: 10;"
            );
        } else {
            box.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-background-radius: 10;");
        }

        Label lbl = new Label(title);
        lbl.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        lbl.setTextFill(Color.web(revealed ? ACCENT_GOLD : TEXT_DIM));
        lbl.setAlignment(Pos.CENTER);
        box.getChildren().add(lbl);

        if (pile.isEmpty()) {
            Label empty = new Label("- empty -");
            empty.setTextFill(Color.web(TEXT_DIM));
            empty.setFont(Font.font("Georgia", 13));
            box.getChildren().add(empty);
            return;
        }

        if (revealed) {
            // overlapping stack: each card offset downward, hover brings it to front
            // height = one full card + (n-1) * offset so the pile is scrollable
            double cardH    = 473;
            double nameH    = 30;
            double offsetY  = 65; // how many px each card peeks below the one above
            int    n        = pile.size();

            // total pane height: full top card + name strip for each subsequent card
            double paneH = cardH + nameH + (n - 1) * offsetY;

            Pane overlapPane = new Pane();
            overlapPane.setPrefSize(360, paneH);
            overlapPane.setMinSize(360, paneH);

            for (int i = 0; i < n; i++) {
                final int idx = i;
                Card card = pile.get(i);

                ImageView iv = new ImageView();
                iv.setFitWidth(340);
                iv.setFitHeight(473);
                iv.setPreserveRatio(true);

                // MTG cards are 63x88mm with 3mm corner radius (~4.8% of width)
                // At 340px wide: radius = ~16px, arcWidth/Height (diameter) = 32
                Rectangle clip = new Rectangle(340, 473);
                clip.setArcWidth(32);
                clip.setArcHeight(32);
                iv.setClip(clip);

                DropShadow ds = new DropShadow(18, Color.BLACK);
                iv.setEffect(ds);

                String cacheKey = card.name + "|" + card.setCode;

                // name overlay while loading
                Label loadingName = new Label(card.name);
                loadingName.setTextFill(Color.WHITE);
                loadingName.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
                loadingName.setWrapText(true);
                loadingName.setMaxWidth(300);
                loadingName.setAlignment(Pos.CENTER);
                loadingName.setStyle(
                        "-fx-background-color: rgba(0,0,0,0.55);" +
                                "-fx-background-radius: 6;" +
                                "-fx-padding: 6 10 6 10;"
                );
                loadingName.setLayoutX(10);
                loadingName.setLayoutY(i * offsetY + 200);

                if (imageCache.containsKey(cacheKey)) {
                    iv.setImage(imageCache.get(cacheKey));
                    loadingName.setVisible(false);
                } else {
                    iv.setImage(cardBackImage);
                    loadCardImageAsync(card, cacheKey, iv, loadingName);
                }

                // position card
                iv.setLayoutX(10);
                iv.setLayoutY(i * offsetY);

                // hover: bring card (and its name overlay) to front
                iv.setOnMouseEntered(e -> {
                    iv.toFront();
                    loadingName.toFront();
                    iv.setEffect(new DropShadow(28, Color.web(ACCENT_GOLD, 0.7)));
                });
                iv.setOnMouseExited(e -> {
                    iv.setEffect(new DropShadow(18, Color.BLACK));
                    restoreZOrder(overlapPane, n);
                });

                overlapPane.getChildren().addAll(iv, loadingName);
            }

            box.getChildren().add(overlapPane);

        } else {
            StackPane stack = new StackPane();
            stack.setMinHeight(260);
            for (int i = pile.size() - 1; i >= 0; i--) {
                ImageView back = new ImageView(cardBackImage);
                back.setFitWidth(260);
                back.setFitHeight(362);
                back.setPreserveRatio(true);
                // 260px wide: 260 * 0.048 * 2 = ~25px arc diameter
                Rectangle backClip = new Rectangle(260, 362);
                backClip.setArcWidth(25);
                backClip.setArcHeight(25);
                back.setClip(backClip);
                StackPane.setMargin(back, new Insets(i * 6.0, 0, 0, i * 4.0));
                stack.getChildren().add(back);
            }
            box.getChildren().add(stack);

            Label countLbl = new Label(pile.size() + (pile.size() != 1 ? " cards" : " card"));
            countLbl.setTextFill(Color.web(TEXT_DIM));
            countLbl.setFont(Font.font("Georgia", 13));
            box.getChildren().add(countLbl);
        }
    }

    // restores z-order of overlapping cards back to original (index 0 = bottom)
    private void restoreZOrder(Pane pane, int n) {
        // children are already the cards in original order; just re-set their order
        List<javafx.scene.Node> children = new ArrayList<>(pane.getChildren());
        pane.getChildren().clear();
        pane.getChildren().addAll(children);
    }

    private VBox buildCardNode(Card card) {
        ImageView iv = new ImageView();
        iv.setFitWidth(340);
        iv.setFitHeight(473);
        iv.setPreserveRatio(true);

        // MTG cards are 63x88mm with 3mm corner radius (~4.8% of width)
        // At 340px wide: radius = ~16px, arcWidth/Height (diameter) = 32
        Rectangle clip = new Rectangle(340, 473);
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        iv.setClip(clip);

        iv.setEffect(new DropShadow(18, Color.BLACK));

        String cacheKey = card.name + "|" + card.setCode;

        // name overlay shown on top of card back while image is loading
        Label loadingName = new Label(card.name);
        loadingName.setTextFill(Color.WHITE);
        loadingName.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        loadingName.setWrapText(true);
        loadingName.setMaxWidth(300);
        loadingName.setAlignment(Pos.CENTER);
        loadingName.setStyle(
                "-fx-background-color: rgba(0,0,0,0.55);" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 6 10 6 10;"
        );

        StackPane cardStack = new StackPane(iv, loadingName);
        cardStack.setAlignment(Pos.CENTER);

        if (imageCache.containsKey(cacheKey)) {
            iv.setImage(imageCache.get(cacheKey));
            loadingName.setVisible(false);
        } else {
            iv.setImage(cardBackImage);
            loadCardImageAsync(card, cacheKey, iv, loadingName);
        }

        // card name label shown below the image (always visible)
        Label nameLbl = new Label(card.name);
        nameLbl.setTextFill(Color.web(TEXT_LIGHT));
        nameLbl.setFont(Font.font("Georgia", 13));
        nameLbl.setWrapText(true);
        nameLbl.setMaxWidth(340);
        nameLbl.setAlignment(Pos.CENTER);

        VBox node = new VBox(6, cardStack, nameLbl);
        node.setAlignment(Pos.CENTER);
        node.setPadding(new Insets(8));
        return node;
    }

    // ── Scryfall image fetching (HttpURLConnection, compatible with Java 8) ──
    private void loadCardImageAsync(final Card card, final String cacheKey, final ImageView iv, final Label loadingName) {
        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                String encodedName = URLEncoder.encode(card.name, "UTF-8");
                String[] urls = {
                        "https://api.scryfall.com/cards/named?exact=" + encodedName + "&set=" + card.setCode,
                        "https://api.scryfall.com/cards/named?exact=" + encodedName
                };

                int maxRetries = 4;
                for (String urlString : urls) {
                    for (int attempt = 0; attempt < maxRetries; attempt++) {
                        if (attempt > 0) {
                            // exponential backoff: 2s, 4s, 8s
                            long delay = (long) Math.pow(2, attempt) * 1000L;
                            System.out.println("Retrying " + card.name + " in " + delay + "ms (attempt " + (attempt + 1) + ")");
                            Thread.sleep(delay);
                        }

                        FetchResult result = fetchScryfallImageUrl(urlString);

                        if (result.imageUrl != null) {
                            return result.imageUrl;
                        }

                        if (result.statusCode == 429) {
                            // respect Retry-After if present, otherwise back off
                            long retryAfter = result.retryAfterSeconds > 0
                                    ? result.retryAfterSeconds * 1000L
                                    : (long) Math.pow(2, attempt + 1) * 1000L;
                            System.out.println("Rate limited fetching " + card.name + ", waiting " + retryAfter + "ms");
                            Thread.sleep(retryAfter);
                            // don't count this as a normal attempt — decrement so we retry
                            attempt--;
                            continue;
                        }

                        if (result.statusCode == 404) {
                            // card not found in this set — break inner loop, try next URL
                            break;
                        }

                        // other errors (500, timeout, etc.) — retry with backoff
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            String imageUrl = task.getValue();
            if (imageUrl == null) {
                System.err.println("Could not load image for: " + card.name + " (all retries exhausted)");
                return;
            }
            final Image img = new Image(imageUrl, 340, 473, true, true, true);
            imageCache.put(cacheKey, img);
            img.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) {
                    Platform.runLater(new Runnable() {
                        public void run() {
                            iv.setImage(img);
                            loadingName.setVisible(false);
                        }
                    });
                }
            });
            if (img.getProgress() >= 1.0) {
                Platform.runLater(new Runnable() {
                    public void run() {
                        iv.setImage(img);
                        loadingName.setVisible(false);
                    }
                });
            }
        });

        task.setOnFailed(e -> System.err.println("Image load task failed for: " + card.name));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // result holder so fetchScryfallImageUrl can return both the URL and the status code
    private static class FetchResult {
        String imageUrl;
        int    statusCode;
        long   retryAfterSeconds;

        FetchResult(String imageUrl, int statusCode, long retryAfterSeconds) {
            this.imageUrl          = imageUrl;
            this.statusCode        = statusCode;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    // makes an HTTP GET to the given Scryfall URL and returns a FetchResult
    private FetchResult fetchScryfallImageUrl(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "WinstonDraft/1.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int status = conn.getResponseCode();

            if (status == 429) {
                long retryAfter = 0;
                String retryHeader = conn.getHeaderField("Retry-After");
                if (retryHeader != null) {
                    try { retryAfter = Long.parseLong(retryHeader.trim()); } catch (NumberFormatException ignored) {}
                }
                return new FetchResult(null, 429, retryAfter);
            }

            if (status != 200) {
                return new FetchResult(null, status, 0);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            String body = sb.toString();

            // single-faced cards — use large for best quality, fall back to normal
            String imageUrl = extractJsonString(body, "large");
            if (imageUrl == null) imageUrl = extractJsonString(body, "normal");

            // double-faced cards
            if (imageUrl == null) {
                int facesIdx = body.indexOf("\"card_faces\"");
                if (facesIdx != -1) {
                    imageUrl = extractJsonString(body.substring(facesIdx), "large");
                    if (imageUrl == null)
                        imageUrl = extractJsonString(body.substring(facesIdx), "normal");
                }
            }

            return new FetchResult(imageUrl, 200, 0);

        } catch (Exception e) {
            System.err.println("HTTP request failed for " + urlString + ": " + e.getMessage());
            return new FetchResult(null, -1, 0);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // minimal JSON string extractor — finds "key":"value" and returns value
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end   = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end).replace("\\/", "/");
    }
}
