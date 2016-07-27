/**
 * Created by slava on 7/11/16.
 */
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.DefaultApi10a;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.*;

/**
 * Code sample for accessing the Yelp API V2.
 *
 * This program demonstrates the capability of the Yelp API version 2.0 by using the Search API to
 * query for businesses by a search term and location, and the Business API to query additional
 * information about the top result from the search query.
 *
 * <p>
 * See <a href="http://www.yelp.com/developers/documentation">Yelp Documentation</a> for more info.
 *
 */
public class YelpAPI {

    private static final String DEFAULT_TERM = "dinner";
    private static final String DEFAULT_LOCATION = "San Francisco, CA";
    private static final String CONFIG_FILE_NAME = "/Users/slava/proto/data_collection/collector/config.json";
    private String consumerKey;
    private String consumerSecret;
    private String token;
    private String tokenSecret;
    private String searchEndpoint;
    private String businessEndpoint;
    private String searchLimit;
    private String apiHost;

    private void init() {
        try {
            BufferedReader br = getFileReader(CONFIG_FILE_NAME);
            String config = br.readLine();
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(config);

            consumerKey = obj.get("consumer_key").toString();
            consumerSecret = obj.get("consumer_secret").toString();
            token = obj.get("token").toString();
            tokenSecret = obj.get("token_secret").toString();

            searchEndpoint = obj.get("search_endpoint").toString();
            businessEndpoint = obj.get("business_endpoint").toString();

            searchLimit = obj.get("default_search_results").toString();

            apiHost = obj.get("api_host").toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    OAuthService service;
    Token accessToken;

    public YelpAPI() throws IOException, ParseException {
        init();
        this.service = new ServiceBuilder().provider(TwoStepOAuth.class).apiKey(consumerKey).apiSecret(consumerSecret).build();
        this.accessToken = new Token(token, tokenSecret);
    }

    /**
     * Creates and sends a request to the Search API by term and location.
     * <p>
     * See <a href="http://www.yelp.com/developers/documentation/v2/search_api">Yelp Search API V2</a>
     * for more info.
     *
     * @param term <tt>String</tt> of the search term to be queried
     * @param location <tt>String</tt> of the location
     * @return <tt>String</tt> JSON Response
     */
    public String searchForBusinessesByLocation(String term, String location) {
        OAuthRequest request = createOAuthRequest(searchEndpoint);
        request.addQuerystringParameter("term", term);
        request.addQuerystringParameter("location", location);
        request.addQuerystringParameter("limit", String.valueOf(searchLimit));
        return sendRequestAndGetResponse(request);
    }

    /**
     * Creates and sends a request to the Business API by business ID.
     * <p>
     * See <a href="http://www.yelp.com/developers/documentation/v2/business">Yelp Business API V2</a>
     * for more info.
     *
     * @param businessID <tt>String</tt> business ID of the requested business
     * @return <tt>String</tt> JSON Response
     */
    public String searchByBusinessId(String businessID) {
        OAuthRequest request = createOAuthRequest(businessEndpoint + "/" + businessID);
        return sendRequestAndGetResponse(request);
    }

    private static BufferedReader getFileReader(String fileName) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(fileName);
        //Construct BufferedReader from InputStreamReader
        return new BufferedReader(new InputStreamReader(fis));
    }

    /**
     * Creates and returns an {@link OAuthRequest} based on the API endpoint specified.
     *
     * @param path API endpoint to be queried
     * @return <tt>OAuthRequest</tt>
     */
    private OAuthRequest createOAuthRequest(String path) {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://" + apiHost + path);
        return request;
    }

    /**
     * Sends an {@link OAuthRequest} and returns the {@link Response} body.
     *
     * @param request {@link OAuthRequest} corresponding to the API request
     * @return <tt>String</tt> body of API response
     */
    private String sendRequestAndGetResponse(OAuthRequest request) {
        System.out.println("Querying " + request.getCompleteUrl() + " ...");
        this.service.signRequest(this.accessToken, request);
        Response response = request.send();
        return response.getBody();
    }

    /**
     * Queries the Search API based on the command line arguments and takes the first result to query
     * the Business API.
     *
     * @param yelpApi <tt>YelpAPI</tt> service instance
     * @param yelpApiCli <tt>YelpAPICLI</tt> command line arguments
     */
    private static void queryAPI(YelpAPI yelpApi, YelpAPICLI yelpApiCli) {
        String searchResponseJSON =
                yelpApi.searchForBusinessesByLocation(yelpApiCli.term, yelpApiCli.location);

        JSONParser parser = new JSONParser();
        JSONObject response = null;
        try {
            response = (JSONObject) parser.parse(searchResponseJSON);
        } catch (ParseException pe) {
            System.out.println("Error: could not parse JSON response:");
            System.out.println(searchResponseJSON);
            System.exit(1);
        }

        JSONArray businesses = (JSONArray) response.get("businesses");
        for (Object business : businesses) {
            JSONObject foundBusiness = (JSONObject) business;
            String businessID = foundBusiness.get("id").toString();
            System.out.println(String.format("Querying business info for the \"%s\" ID ...", businessID));

            String businessResponseJSON = yelpApi.searchByBusinessId(businessID);
            System.out.println(String.format("Result for business \"%s\" found:", businessID));
            System.out.println(businessResponseJSON);
        }
    }

    /**
     * Command-line interface for the sample Yelp API runner.
     */
    private static class YelpAPICLI {
        @Parameter(names = {"-q", "--term"}, description = "Search Query Term")
        public String term = DEFAULT_TERM;

        @Parameter(names = {"-l", "--location"}, description = "Location to be Queried")
        public String location = DEFAULT_LOCATION;
    }

    /**
     * Main entry for sample Yelp API requests.
     * <p>
     * After entering your OAuth credentials, execute <tt><b>run.sh</b></tt> to run this example.
     */
    public static void main(String[] args) {
        YelpAPICLI yelpApiCli = new YelpAPICLI();
        new JCommander(yelpApiCli, args);

        try {
            YelpAPI yelpApi = new YelpAPI();
            queryAPI(yelpApi, yelpApiCli);
        } catch (Exception e) {
            System.out.println("Failure!!! " + e.toString());
        }
    }
}