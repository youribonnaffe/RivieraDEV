package controllers;

import java.util.ArrayList;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import models.Configuration;
import models.ConfigurationKey;
import models.Language;
import models.Level;
import models.News;
import models.Organiser;
import models.PreviousSpeaker;
import models.PricePack;
import models.PricePackCurrentState;
import models.PricePackDate;
import models.PricePackPeriod;
import models.Slot;
import models.Speaker;
import models.Sponsor;
import models.SponsorShip;
import models.Talk;
import models.TalkTheme;
import models.TalkType;
import models.TemporarySlot;
import models.Track;

import play.i18n.Lang;
import play.mvc.Before;
import play.mvc.Controller;

import util.DateUtils;
import util.StringUtils;

public class Application extends Controller {

    @Before
    private static void setup() {
        renderArgs.put("user", Security.connected());
        renderArgs.put("promotedPage", getPromotedPage());
        renderArgs.put("displayTalks", displayTalks());
        renderArgs.put("ticketingIsOpen", ticketingIsOpen());
        renderArgs.put("displayNewSpeakers", displayNewSpeakers());
        renderArgs.put("cfpIsOpened", cfpIsOpened());
        renderArgs.put("cfpUrl", getCfpUrl());
    }

    public static void fr(String url) {
        Lang.change("fr");
        redirect(url);
    }

    public static void en(String url) {
        Lang.change("en");
        redirect(url);
    }

    public static void index() {
        String promotedPage2 = getPromotedPage2();

        String eventStartDateStr = getEventStartDate();
        String eventEndDateStr = getEventEndDate();
        boolean displayCountdown = false;
        if (StringUtils.isNotBlank(eventStartDateStr) && StringUtils.isNotBlank(eventEndDateStr)) {
            SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            Date now = new Date();
            try {
                Date eventStartDate = isoDateFormat.parse(eventStartDateStr);
                Date eventEndDate = isoDateFormat.parse(eventEndDateStr); // Just test that the format is correct
                displayCountdown = now.before(eventStartDate);
            } catch (ParseException e) {
                e.printStackTrace();
                // Do nothing more, displayCountdown is already set to false
            }
        }

        String googleMapApiKey = getGoogleMapApiKey();

        News latestNews = News.latest();

        SponsorsToDisplay sponsorsToDisplay = getSponsorsToDisplay();
        Map<SponsorShip, List<Sponsor>> sponsors = sponsorsToDisplay.getSponsors();
        List<Sponsor> sponsorsPreviousYears = sponsorsToDisplay.getSponsorsPreviousYears();
        List<Speaker> speakersPreviousYears = PreviousSpeaker.find("ORDER BY lastName, firstName").fetch();
        List<Speaker> speakersStar = Speaker.find("star = true ORDER BY lastName, firstName").fetch();

        boolean lunchesAndPartySoldOut = sponsors.get(SponsorShip.Lunches) != null
                && sponsors.get(SponsorShip.Lunches).size() > 0 && sponsors.get(SponsorShip.Party) != null
                && sponsors.get(SponsorShip.Party).size() > 0;

        boolean displayPreviousSpeakers = !displayNewSpeakers();

        String sponsoringLeafletUrl = getSponsoringLeafletUrl();
        
        String cancelledUrl = getCancelledUrl();

        render(promotedPage2, displayCountdown, eventStartDateStr, eventEndDateStr, googleMapApiKey,
                displayPreviousSpeakers, sponsors, lunchesAndPartySoldOut, sponsorsPreviousYears, speakersPreviousYears,
                speakersStar, latestNews, sponsoringLeafletUrl, cancelledUrl);
    }

    public static void news() {
        List<News> news = News.byDate();
        render(news);
    }

    public static void photos() {
        render();
    }

    public static void about() {
        organisers();
    }

    public static void access() {
        render();
    }

    public static void cfp() {
        List<Organiser> orgas = Organiser.cfp();
        render(orgas);
    }

    public static void coc() {
        render();
    }

    public static void subscribe() {
        String ticketingUrl = getTicketingUrl();
        boolean ticketingIsOpen = ticketingIsOpen();
        String ticketingTrainingUrl = getTicketingTrainingUrl();
        boolean ticketingTrainingIsOpen = ticketingTrainingIsOpen();

        List<PricePack> pricePacks = PricePack.findAll();
        List<PricePackDate> pricePackDatesList = PricePackDate.findAll();
        PricePackDate pricePackDates = null;
        if (pricePackDatesList != null && pricePackDatesList.size() >= 1) {
            pricePackDates = pricePackDatesList.get(0);
        }
        List<PricePackCurrentState> pricePackCurrentStateList = new ArrayList<PricePackCurrentState>();
        Date now = new Date();
        for (PricePack pricePack : pricePacks) {
            Integer currentPrice = null;
            Integer maxPrice = null;
            PricePackPeriod currentPeriod = null;
            Long remainingDays = null;
            if (now.before(pricePackDates.blindBirdEndDate)) {
                currentPeriod = PricePackPeriod.BLIND_BIRD;
                currentPrice = pricePack.blindBirdPrice;
                maxPrice = pricePack.regularPrice;
                remainingDays = DateUtils.getDaysBetweenDates(now, pricePackDates.blindBirdEndDate);
            } else if (now.before(pricePackDates.earlyBirdEndDate)) {
                currentPeriod = PricePackPeriod.EARLY_BIRD;
                currentPrice = pricePack.earlyBirdPrice;
                maxPrice = pricePack.regularPrice;
                remainingDays = DateUtils.getDaysBetweenDates(now, pricePackDates.earlyBirdEndDate);
            } else {
                currentPeriod = PricePackPeriod.REGULAR;
                currentPrice = pricePack.regularPrice;
                if (now.before(pricePackDates.regularEndDate)) {
                    remainingDays = DateUtils.getDaysBetweenDates(now, pricePackDates.regularEndDate);
                }
            }
            pricePackCurrentStateList.add(new PricePackCurrentState(pricePack.type, currentPrice, maxPrice,
                    pricePack.studentPrice, currentPeriod, remainingDays, pricePack.soldOut));
        }

        render(ticketingUrl, ticketingIsOpen, ticketingTrainingUrl, ticketingTrainingIsOpen, pricePackCurrentStateList);
    }

    public static void schedule() {
        boolean displayFullSchedule = displayFullSchedule();
        boolean displayNewSpeakers = displayNewSpeakers();
        boolean displayTalks = displayTalks();

        List<Date> days = null;
        if (!displayFullSchedule) {
            days = TemporarySlot.find(
                    "select distinct date_trunc('day', startDate) from TemporarySlot ORDER BY date_trunc('day', startDate)")
                    .fetch();
        } else {
            days = Slot.find(
                    "select distinct date_trunc('day', startDate) from Slot ORDER BY date_trunc('day', startDate)")
                    .fetch();
        }
        List<Track> tracks = Track.findAll();
        Collections.sort(tracks);
        List<TalkTheme> themes = TalkTheme.findUsedThemes();
        List<TalkType> types = TalkType.findUsedTypes();
        Collections.sort(types);
        Level[] levels = Level.values();
        Language[] languages = Language.values();
        Map<Date, List<Track>> tracksPerDays = new HashMap<Date, List<Track>>();
        for (Date day : days) {
            List<Track> tracksPerDay = Talk.findTracksPerDay(day);
            Collections.sort(tracksPerDay);
            tracksPerDays.put(day, tracksPerDay);
        }

        render(displayFullSchedule, displayNewSpeakers, displayTalks, days, tracks, languages, tracksPerDays, themes, types, levels);
    }

    public static void scheduleSuperSecret() {
        List<Date> days = Slot
                .find("select distinct date_trunc('day', startDate) from Slot ORDER BY date_trunc('day', startDate)")
                .fetch();
        List<Track> tracks = Track.findAll();
        List<TalkTheme> themes = TalkTheme.findUsedThemes();
        List<TalkType> types = TalkType.findUsedTypes();
        Collections.sort(types);
        Level[] levels = Level.values();
        Map<Date, List<Track>> tracksPerDays = new HashMap<Date, List<Track>>();
        for (Date day : days) {
            List<Track> tracksPerDay = Talk.findTracksPerDay(day);
            Collections.sort(tracksPerDay);
            tracksPerDays.put(day, tracksPerDay);
        }
        Language[] languages = Language.values();
        render(days, tracks, tracksPerDays, themes, types, levels, languages);
    }

    public static void talks() {
        if (!displayTalks()) {
            index();
        }

        List<TalkTheme> themes = TalkTheme.findUsedThemes();
        Level[] levels = Level.values();
        List<TalkType> types = TalkType.findUsedTypes();
        Collections.sort(types);
        List<Talk> talks = Talk.find("isHiddenInTalksPage = false").fetch();
        Collections.sort(talks);
        boolean displayFullSchedule = displayFullSchedule();
        Language[] languages = Language.values();
        render(themes, levels, talks, types, languages, displayFullSchedule);
    }

    public static void speakers() {
        List<Speaker> speakers = Speaker.find("ORDER BY lastName, firstName").fetch();
        List<Speaker> speakersPreviousYears = PreviousSpeaker.find("ORDER BY lastName, firstName").fetch();
        boolean displayPreviousSpeakers = !displayNewSpeakers();

        render(speakers, speakersPreviousYears, displayPreviousSpeakers);
    }

    public static void speaker(Long id) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);
        render(speaker);
    }

    public static void sponsor(Long id) {
        Sponsor sponsor = Sponsor.findById(id);
        notFoundIfNull(sponsor);
        render(sponsor);
    }

    public static void talk(Long id) {
        Talk talk = Talk.findById(id);
        notFoundIfNull(talk);
        boolean displayFullSchedule = displayFullSchedule();
        render(talk, displayFullSchedule);
    }

    public static void judcon() {
        List<Speaker> speakers = Speaker.find(
                "FROM Speaker speaker JOIN speaker.talks talk WHERE talk.track.isJUDCon = true ORDER BY lastName, firstName")
                .fetch();

        List<Talk> talks = Talk.find("track.isJUDCon = true AND isHiddenInTalksPage = false").fetch();
        Collections.sort(talks);

        render(speakers, talks);
    }

    public static void sponsors() {
        // Redirect to an anchor on home page
        redirect("/#sponsors");

        // Keep old code because previous sponsors is not yet implemented on new site
        // SponsorsToDisplay sponsorsToDisplay = getSponsorsToDisplay();
        // Map<SponsorShip, List<Sponsor>> sponsors = sponsorsToDisplay.getSponsors();
        // List<Sponsor> sponsorsPreviousYears =
        // sponsorsToDisplay.getSponsorsPreviousYears();

        // render(sponsors, sponsorsPreviousYears);
    }

    public static void becomeSponsor() {
        String sponsoringLeafletUrl = getSponsoringLeafletUrl();

        // Until becomeSponsor.html has a new design
        redirect(sponsoringLeafletUrl);

        // render(sponsoringLeafletUrl);
    }

    public static void previousSpeakerPhoto(Long id) {
        PreviousSpeaker speaker = PreviousSpeaker.findById(id);
        notFoundIfNull(speaker);
        if (!speaker.photo.exists())
            redirect("/public/images/mascotte/Ray_Cool.jpg");
        response.contentType = speaker.photo.type();
        renderBinary(speaker.photo.get());
    }

    public static void speakerPhoto(Long id) {
        Speaker speaker = Speaker.findById(id);
        notFoundIfNull(speaker);
        if (!speaker.photo.exists())
            redirect("/public/images/mascotte/Ray_Cool.jpg");
        response.contentType = speaker.photo.type();
        renderBinary(speaker.photo.get());
    }

    public static void sponsorLogo(Long id) {
        Sponsor sponsor = Sponsor.findById(id);
        notFoundIfNull(sponsor);
        response.contentType = sponsor.logo.type();
        renderBinary(sponsor.logo.get());
    }

    public static void orgaPhoto(Long id) {
        Organiser organiser = Organiser.findById(id);
        notFoundIfNull(organiser);
        if (!organiser.photo.exists())
            redirect("/public/images/mascotte/Ray_Badass.jpg");
        response.contentType = organiser.photo.type();
        renderBinary(organiser.photo.get());
    }

    public static void organisers() {
        List<Organiser> orgas = Organiser.organisers();
        render(orgas);
    }

    public static void organiser(Long id) {
        Organiser orga = Organiser.findById(id);
        notFoundIfNull(orga);
        render(orga);
    }

    public static void fishMarket(String date) throws ParseException {
        Date day = new Date(System.currentTimeMillis());
        if (date != null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            day = format.parse(date);
        }
        List<Track> tracksForDay = Talk.findTracksPerDay(day);
        render(tracksForDay, day);
    }

    public static Integer likeTalk(Long id) {
        Talk talk = Talk.findById(id);
        if (talk != null) {
            talk.like();
        }
        if (Security.connected() != null) {
            // Return nb of likes only for connected user (i.e. admin)
            return talk.nbLikes;
        }
        return null;
    }

    public static Integer unlikeTalk(Long id) {
        Talk talk = Talk.findById(id);
        if (talk != null) {
            talk.unlike();
        }
        if (Security.connected() != null) {
            // Return nb of likes only for connected user (i.e. admin)
            return talk.nbLikes;
        }
        return null;
    }

    private static SponsorsToDisplay getSponsorsToDisplay() {
        boolean mustDisplaySponsorsPreviousYears = true;

        Map<SponsorShip, List<Sponsor>> sponsors = new TreeMap<SponsorShip, List<Sponsor>>();
        for (SponsorShip sponsorShip : SponsorShip.values()) {
            if (sponsorShip != SponsorShip.PreviousYears) {
                List<Sponsor> sponsorsBySponsorShip = Sponsor.find("level", sponsorShip).fetch();
                if (sponsorsBySponsorShip != null && sponsorsBySponsorShip.size() > 0) {
                    mustDisplaySponsorsPreviousYears = false;
                    Collections.sort(sponsorsBySponsorShip);
                    sponsors.put(sponsorShip, sponsorsBySponsorShip);
                }
            }
        }

        List<Sponsor> sponsorsPreviousYears = null;
        if (mustDisplaySponsorsPreviousYears) {
            sponsorsPreviousYears = Sponsor.find("level", SponsorShip.PreviousYears).fetch();
            Collections.sort(sponsorsPreviousYears);
        }

        return new SponsorsToDisplay(sponsors, sponsorsPreviousYears);
    }

    /**
     * Retourne l'API KEY sauvée en BD. En local, si la clé n'est pas définie alors
     * la google map fonctionne quand même. MAIS en Prod/Staging, il FAUT une API
     * Key sinon la carte ne fonctionne pas c'est certainement une restriction
     * google.
     * 
     * L'API KEY de Prod ne peut pas être utilisée en local, car nous l'avons
     * restreinte pour ne fonctionner qu'avec les domaines *.rivieradev.fr et
     * *.rivieradev.com afin de suivre les recommandations de sécurité décrites par
     * Google.
     * 
     * Pour générer une nouvelle API KEY :
     * https://developers.google.com/maps/documentation/javascript/get-api-key?hl=Fr
     */
    private static String getGoogleMapApiKey() {
        Configuration config = Configuration.find("key", ConfigurationKey.GOOGLE_MAP_API_KEY).first();
        if (config != null) {
            return config.value;
        }
        return "";
    }

    /**
     * Retourne la date de début de la conférence telle qu'elle est stockée en BD.
     * Elle devrait être au format ISO. Ex: 2019-05-15T08:20:00
     * 
     * @return la date de début de la conférence
     */
    private static String getEventStartDate() {
        Configuration config = Configuration.find("key", ConfigurationKey.EVENT_START_DATE).first();
        if (config != null) {
            return config.value;
        }
        return "";
    }

    /**
     * Retourne la date de fin de la conférence telle qu'elle est stockée en BD.
     * Elle devrait être au format ISO. Ex: 2019-05-15T08:20:00
     * 
     * @return la date de fin de la conférence
     */
    private static String getEventEndDate() {
        Configuration config = Configuration.find("key", ConfigurationKey.EVENT_END_DATE).first();
        if (config != null) {
            return config.value;
        }
        return "";
    }

    /**
     * Retourne true si le programme complet doit être affiché, faux sinon.
     */
    private static boolean displayFullSchedule() {
        Configuration config = Configuration.find("key", ConfigurationKey.DISPLAY_FULL_SCHEDULE).first();
        return config != null && config.value.equals("true");
    }

    /**
     * Retourne true si les speakers de la nouvelle édition doivent être affichés
     * (utile avant que le programme définitif ne soit connu)
     */
    private static boolean displayNewSpeakers() {
        Configuration config = Configuration.find("key", ConfigurationKey.DISPLAY_NEW_SPEAKERS).first();
        return config != null && config.value.equals("true");
    }

    /**
     * Retourne la page à mettre en avant sur la home page et dans le menu. 'CFP' :
     * La page du CFP 'TICKETS' : La page d'achat de tickets 'SPONSORS' : La page
     * pour devenir un sponsor
     */
    private static String getPromotedPage() {
        Configuration config = Configuration.find("key", ConfigurationKey.PROMOTED_PAGE).first();
        return config != null ? config.value : "";
    }

    /**
     * Retourne la 2ème page à mettre en avant sur la home page. 'SPONSORS' : La
     * page pour devenir un sponsor 'SCHEDULE' : Le programme
     */
    private static String getPromotedPage2() {
        Configuration config = Configuration.find("key", ConfigurationKey.PROMOTED_PAGE_2).first();
        return config != null ? config.value : "";
    }

    /**
     * Retourne l'Url de la page où on peut acheter les billets.
     */
    private static String getTicketingUrl() {
        Configuration config = Configuration.find("key", ConfigurationKey.TICKETING_URL).first();
        return config != null ? config.value : "";
    }

    /**
     * Retourne true s'il est possible d'acheter des billets. (utile pour enlever
     * l'accès à la page de vente des billets)
     */
    private static boolean ticketingIsOpen() {
        Configuration config = Configuration.find("key", ConfigurationKey.TICKETING_OPEN).first();
        return config != null && config.value.equals("true");
    }

    /**
     * Retourne l'Url de la page de l'organisme de formation.
     */
    private static String getTicketingTrainingUrl() {
        Configuration config = Configuration.find("key", ConfigurationKey.TICKETING_TRAINING_URL).first();
        return config != null ? config.value : "";
    }

    /**
     * Retourne true s'il est possible d'accéder à la page de l'organisme de formation 
     * (utile en attendant que la page soit prête)
     */
    private static boolean ticketingTrainingIsOpen() {
        Configuration config = Configuration.find("key", ConfigurationKey.TICKETING_TRAINING_OPEN).first();
        return config != null && config.value.equals("true");
    }

    /**
     * Return true if Call For Paper is opened, false otherwise
     */
    private static boolean cfpIsOpened() {
        Configuration config = Configuration.find("key", ConfigurationKey.CFP_OPEN).first();
        return config != null && config.value.equals("true");
    }

    /**
     * Return the Call for Paper URL
     */
    private static String getCfpUrl() {
        Configuration config = Configuration.find("key", ConfigurationKey.CFP_URL).first();
        return config != null ? config.value : "";
    }

    /**
     * Retourne true si le menu doit permettre d'afficher la page des talks (utile
     * pour enlever l'accès à la page tant qu'on n'a pas encore de talks)
     */
    private static boolean displayTalks() {
        Configuration config = Configuration.find("key", ConfigurationKey.DISPLAY_TALKS).first();
        return config != null && config.value.equals("true");
    }

    private static String getSponsoringLeafletUrl() {
        Configuration config = Configuration.find("key", ConfigurationKey.SPONSORING_LEAFLET_URL).first();
        return config != null ? config.value : "";
    }

    private static class SponsorsToDisplay {
        private Map<SponsorShip, List<Sponsor>> sponsors;
        private List<Sponsor> sponsorsPreviousYears;

        public SponsorsToDisplay(Map<SponsorShip, List<Sponsor>> sponsors, List<Sponsor> sponsorsPreviousYears) {
            this.sponsors = sponsors;
            this.sponsorsPreviousYears = sponsorsPreviousYears;
        }

        public Map<SponsorShip, List<Sponsor>> getSponsors() {
            return this.sponsors;
        }

        public List<Sponsor> getSponsorsPreviousYears() {
            return this.sponsorsPreviousYears;
        }
    }

    public static void live() {
        List<Track> tracks = Track.findAll();
        Collections.sort(tracks);
        render(tracks);
    }

    public static void liveTrack(String track) {
        List<Track> tracks = Track.findAll();
        Collections.sort(tracks);
        List<Talk> keynotes = Talk.findKeynotes();
        render(tracks, track, keynotes);
    }

    public static void voteForTalks() {
        List<Talk> talks = Talk.find("isHiddenInTalksPage = false").fetch();
        Collections.sort(talks);
        render(talks);
    }

    public static void schools() {
        SponsorShip sponsorShip = SponsorShip.Schools;
        List<Sponsor> sponsors = Sponsor.find("level", sponsorShip).fetch();
        render(sponsorShip, sponsors);
    }
    
    /**
     * Covid19 newsletter URL
     */
    private static String getCancelledUrl() {
        Configuration config = Configuration.find("key", ConfigurationKey.CANCELLED_URL).first();
        return config != null ? config.value : "";
    }
}
