package com.shopsmart.seed;

import java.util.Map;
import java.util.Random;

/** Varied, category-aware review texts for seeded reviews. */
final class SeedReviewText {

    private static final String[] GREAT_TITLES = {"Absolutely worth it", "Exceeded my expectations", "Best purchase this year",
            "Premium feel, great value", "Five stars without hesitation", "Love it"};
    private static final String[] GOOD_TITLES = {"Very good overall", "Solid choice", "Happy with the purchase",
            "Does the job well", "Good value for money"};
    private static final String[] OKAY_TITLES = {"Decent, but not perfect", "Average experience", "Okay for the price"};
    private static final String[] BAD_TITLES = {"Disappointed", "Not as expected", "Would not recommend"};

    private static final Map<String, String[][]> COMMENTS = Map.of(
            "smartphones", new String[][] {
                    {"The camera is excellent even at night and the battery easily lasts a full day.",
                            "Super smooth display and the fast charging is a game changer."},
                    {"Great performance for daily use. Camera is good in daylight, average indoors.",
                            "Battery life is solid. Would have liked a slightly lighter phone."},
                    {"Phone is fine but heats up a little while gaming.", "Software has a few bloatware apps."},
                    {"Battery drains faster than advertised.", "Had issues with network connectivity."}},
            "laptops", new String[][] {
                    {"Blazing fast, silent and the display is gorgeous. Perfect for coding.",
                            "Battery lasts my entire workday. Keyboard is a joy to type on."},
                    {"Great build quality and performance. Fans get audible under heavy load.",
                            "Good laptop for college, handles multitasking easily."},
                    {"Decent machine but the webcam could be better.", "Speakers are a bit weak."},
                    {"Runs hot and the trackpad is unreliable.", "Arrived with a dead pixel; replacement took time."}},
            "audio", new String[][] {
                    {"Noise cancellation is outstanding - my commute is finally quiet.",
                            "Rich, balanced sound and they stay comfortable for hours."},
                    {"Great sound for the price. Mic quality is decent on calls.",
                            "Comfortable fit, battery life is as advertised."},
                    {"Sound is okay, bass is a bit muddy.", "Connection drops occasionally with my laptop."},
                    {"Ear cushions started peeling within weeks.", "Left earbud keeps disconnecting."}},
            "wearables", new String[][] {
                    {"Accurate heart rate and sleep tracking. Battery lasts almost two weeks!",
                            "Bright display and the fitness insights keep me motivated."},
                    {"Good tracker for the price, notifications work reliably.",
                            "GPS is accurate on runs. Strap could be softer."},
                    {"Step count seems a little generous.", "App syncing is slow sometimes."},
                    {"Stopped charging after a month.", "Heart rate readings were inconsistent."}},
            "monitors", new String[][] {
                    {"Colours are stunning and text is razor sharp.", "Super smooth for gaming, zero ghosting."},
                    {"Great monitor, stand is sturdy and adjustable.", "Very good picture quality for the price."},
                    {"Backlight bleed in one corner, otherwise fine.", "Built-in speakers are weak."},
                    {"Arrived with a dead pixel.", "Flickers occasionally over HDMI."}},
            "keyboards", new String[][] {
                    {"Typing feel is incredible and it sounds amazing.", "Build quality is superb, wireless is lag-free."},
                    {"Very nice keyboard, keycaps feel premium.", "Great for work and gaming."},
                    {"Software is clunky but the keyboard is fine.", "Stabilisers rattle a little."},
                    {"Some keys double-type after a few weeks.", "Bluetooth keeps disconnecting."}},
            "mice", new String[][] {
                    {"Incredibly light and precise, my aim has improved.", "So comfortable for all-day use."},
                    {"Great mouse for the price, clicks feel crisp.", "Tracks well on every surface I tried."},
                    {"Scroll wheel is a little loud.", "Side buttons are hard to reach."},
                    {"Double-click issue after a month.", "Sensor skips on my mouse pad."}},
            "power-charging", new String[][] {
                    {"Charges my laptop and phone at the same time. Perfect for travel.",
                            "Fast, compact and reliable."},
                    {"Does what it says, charges quickly.", "Good build quality, slightly heavy."},
                    {"Works, but gets warm while charging.", "Takes long to recharge itself."},
                    {"Stopped working after two weeks.", "Does not fast-charge my phone."}},
            "tablets", new String[][] {
                    {"Beautiful display, perfect for Netflix and notes.", "Great for online classes and reading."},
                    {"Good tablet for the price.", "Speakers are surprisingly good."},
                    {"A bit slow when multitasking.", "Charging takes long."},
                    {"Touchscreen became unresponsive.", "Keeps restarting randomly."}},
            "accessories", new String[][] {
                    {"Exactly what my desk setup needed. Excellent quality.", "Works flawlessly with my laptop."},
                    {"Good quality and fairly priced.", "Does the job, no complaints."},
                    {"Okay product, packaging could be better.", "Works but feels a bit cheap."},
                    {"Stopped working after a few uses.", "Not compatible with my device."}});

    private SeedReviewText() {
    }

    static String[] forRating(Random random, int rating, String category) {
        String[][] comments = COMMENTS.getOrDefault(category, COMMENTS.get("accessories"));
        int bucket = rating >= 5 ? 0 : rating == 4 ? 1 : rating == 3 ? 2 : 3;
        String[] titles = switch (bucket) {
            case 0 -> GREAT_TITLES;
            case 1 -> GOOD_TITLES;
            case 2 -> OKAY_TITLES;
            default -> BAD_TITLES;
        };
        String[] options = comments[bucket];
        return new String[] {titles[random.nextInt(titles.length)], options[random.nextInt(options.length)]};
    }
}
