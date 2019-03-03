package com.ibm.pross.server.app.http.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.ibm.pross.common.CommonConfiguration;
import com.ibm.pross.server.app.avpss.ApvssShareholder;
import com.ibm.pross.server.app.http.HttpRequestProcessor;
import com.ibm.pross.server.app.http.HttpStatusCode;
import com.ibm.pross.server.configuration.permissions.AccessEnforcement;
import com.ibm.pross.server.configuration.permissions.ClientPermissions.Permissions;
import com.ibm.pross.server.configuration.permissions.exceptions.BadRequestException;
import com.ibm.pross.server.configuration.permissions.exceptions.NotFoundException;
import com.ibm.pross.server.configuration.permissions.exceptions.UnauthorizedException;
import com.sun.net.httpserver.HttpExchange;

import bftsmart.reconfiguration.util.sharedconfig.KeyLoader;
import bftsmart.reconfiguration.util.sharedconfig.ServerConfiguration;

/**
 * This handler returns the raw content of a secret share. Client's must have a
 * specific authorization to be able to invoke this method. If the share is not
 * found a 404 is returned. If the client is not authorized a 401 is returned.
 */
@SuppressWarnings("restriction")
public class ReadHandler extends AuthenticatedClientRequestHandler {

	public static final Permissions REQUEST_PERMISSION = Permissions.READ;

	// Query name
	public static final String SECRET_NAME_FIELD = "secretName";

	// Fields
	private final AccessEnforcement accessEnforcement;
	private final ServerConfiguration serverConfig;
	private final ConcurrentMap<String, ApvssShareholder> shareholders;

	public ReadHandler(KeyLoader clientKeys, final AccessEnforcement accessEnforcement, final ServerConfiguration serverConfig,
			final ConcurrentMap<String, ApvssShareholder> shareholders) {
		super(clientKeys);
		this.shareholders = shareholders;
		this.serverConfig = serverConfig;
		this.accessEnforcement = accessEnforcement;
	}

	@Override
	public void authenticatedClientHandle(final HttpExchange exchange, final Integer clientId)
			throws IOException, UnauthorizedException, NotFoundException, BadRequestException {

		// Extract secret name from request
		final String queryString = exchange.getRequestURI().getQuery();
		final Map<String, List<String>> params = HttpRequestProcessor.parseQueryString(queryString);
		final List<String> secretNames = params.get(SECRET_NAME_FIELD);
		if (secretNames == null || secretNames.size() != 1) {
			throw new BadRequestException();
		}
		final String secretName = secretNames.get(0);

		// Perform authentication
		accessEnforcement.enforceAccess(clientId, secretName, REQUEST_PERMISSION);

		// Do processing
		final ApvssShareholder shareholder = this.shareholders.get(secretName);
		if (shareholder == null) {
			throw new NotFoundException();
		}

		// Create response
		final String response = readShare(shareholder, secretName, serverConfig);
		final byte[] binaryResponse = response.getBytes(StandardCharsets.UTF_8);

		// Write headers
		exchange.sendResponseHeaders(HttpStatusCode.SUCCESS, binaryResponse.length);

		// Write response
		try (final OutputStream os = exchange.getResponseBody();) {
			os.write(binaryResponse);
		}
	}

	private static String readShare(final ApvssShareholder shareholder, final String secretName,
			final ServerConfiguration serverConfig) throws NotFoundException {

		// This server
		final int serverIndex = shareholder.getIndex();
		final InetSocketAddress thisServerAddress = serverConfig.getServerAddresses().get(serverIndex - 1);
		final String ourIp = thisServerAddress.getHostString();
		final int ourPort = HttpRequestProcessor.BASE_HTTP_PORT + serverIndex;
		final String infoUrl = "https://" + ourIp + ":" + ourPort + "/info?secretName=" + secretName;

		// Create response
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("<html>\n");
		stringBuilder.append("<head>\n");
		stringBuilder.append("<meta http-equiv=\"refresh\" content=\"10\">\n");
		stringBuilder.append("</head>\n");
		stringBuilder.append("<body>\n");
		stringBuilder.append("<tt>\n");

		// Shareholder information
		stringBuilder.append("This is <a href=\"/\">shareholder #" + serverIndex + "</a>"
				+ " running <a href=\"https://github.com/jasonkresch/protect\">PROTECT</a>,"
				+ " a <b>P</b>latform for <b>Ro</b>bust <b>T</b>hr<b>e</b>shold <b>C</b>ryp<b>t</b>ography.\n");
		stringBuilder.append("<p/>\n");

		// Secret Info
		stringBuilder.append("<b>Share #" + serverIndex + " of \"<a href=\"" + infoUrl + "\">" + secretName + "</a>\":</b><br/>\n");
		if (shareholder.getSecretPublicKey() == null) {
			throw new NotFoundException();
		} else {
			// Print share information
			stringBuilder.append("s_" + serverIndex + "     =  " + shareholder.getShare1().getY() + "<br/>\n");
			stringBuilder.append("epoch        =  " + shareholder.getEpoch() + "<br/>\n");
			stringBuilder.append("last_refresh =  " + shareholder.getLastRefreshTime() + "<br/>\n");
			stringBuilder.append("<p/>\n");

			// Print Field Information
			stringBuilder.append("<b>Field Information:</b><br/>\n");
			stringBuilder.append("prime_modulus    =  " + CommonConfiguration.CURVE.getR() + "<br/>\n");
			stringBuilder.append("curve_oid        =  " + CommonConfiguration.CURVE.getOid() + " ("
					+ CommonConfiguration.CURVE.getName() + ")<br/>\n");
			stringBuilder.append("<p/>\n");
		}

		// Peers
		stringBuilder.append("<p/>\n");
		stringBuilder.append("<b>Peers:</b><br/>\n");

		int serverId = 0;
		for (final InetSocketAddress serverAddress : serverConfig.getServerAddresses()) {
			serverId++;
			final String serverIp = serverAddress.getHostString();
			final int serverPort = HttpRequestProcessor.BASE_HTTP_PORT + serverId;
			final String linkUrl = "https://" + serverIp + ":" + serverPort + "/read?secretName=" + secretName;
			stringBuilder.append(
					"server." + serverId + " = " + "<a href=\"" + linkUrl + "\">" + serverAddress + "</a><br/>\n");
		}
		stringBuilder.append("<p/>\n");

		stringBuilder.append("</tt>\n");
		stringBuilder.append("</body>\n");
		stringBuilder.append("</html>\n");

		return stringBuilder.toString();
	}

}