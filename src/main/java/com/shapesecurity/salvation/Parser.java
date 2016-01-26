package com.shapesecurity.salvation;

import com.shapesecurity.salvation.data.*;
import com.shapesecurity.salvation.directiveValues.*;
import com.shapesecurity.salvation.directives.*;
import com.shapesecurity.salvation.tokens.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern WSP = Pattern.compile("[ \t]+");
    private static final Pattern NotWSP = Pattern.compile("[^ \t]+");
    private static final DirectiveParseException MISSING_DIRECTIVE_NAME =
        new DirectiveParseException("Missing directive-name");
    private static final DirectiveParseException INVALID_DIRECTIVE_NAME =
        new DirectiveParseException("Invalid directive-name");
    private static final DirectiveParseException INVALID_DIRECTIVE_VALUE =
        new DirectiveParseException("Invalid directive-value");
    private static final DirectiveParseException INVALID_MEDIA_TYPE_LIST =
        new DirectiveParseException("Invalid media-type-list");
    private static final DirectiveValueParseException INVALID_MEDIA_TYPE =
        new DirectiveValueParseException("Invalid media-type");
    private static final DirectiveParseException INVALID_SOURCE_LIST =
        new DirectiveParseException("Invalid source-list");
    private static final DirectiveValueParseException INVALID_SOURCE_EXPR =
        new DirectiveValueParseException("Invalid source-expression");
    private static final DirectiveParseException INVALID_ANCESTOR_SOURCE_LIST =
        new DirectiveParseException("Invalid ancestor-source-list");
    private static final DirectiveValueParseException INVALID_ANCESTOR_SOURCE =
        new DirectiveValueParseException("Invalid ancestor-source");
    private static final DirectiveParseException INVALID_REFERRER_TOKEN_LIST =
        new DirectiveParseException("Invalid referrer-token list");
    private static final DirectiveValueParseException INVALID_REFERRER_TOKEN =
        new DirectiveValueParseException("Invalid referrer-token");
    private static final DirectiveParseException INVALID_SANDBOX_TOKEN_LIST =
        new DirectiveParseException("Invalid sandbox-token list");
    private static final DirectiveValueParseException INVALID_SANDBOX_TOKEN =
        new DirectiveValueParseException("Invalid sandbox-token");
    private static final DirectiveParseException INVALID_URI_REFERENCE_LIST =
        new DirectiveParseException("Invalid uri-reference list");
    private static final DirectiveValueParseException INVALID_URI_REFERENCE =
        new DirectiveValueParseException("Invalid uri-reference");
    private static final DirectiveParseException NON_EMPTY_VALUE_TOKEN_LIST =
        new DirectiveParseException("Non-empty directive-value list");
    @Nonnull protected final Token[] tokens;
    @Nonnull private final Origin origin;
    protected int index = 0;
    @Nullable protected Collection<Notice> noticesOut;

    protected Parser(@Nonnull Token[] tokens, @Nonnull Origin origin, @Nullable Collection<Notice> noticesOut) {
        this.origin = origin;
        this.tokens = tokens;
        this.noticesOut = noticesOut;
    }

    @Nonnull public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin) {
        return new Parser(Tokeniser.tokenise(sourceText), origin, null).parsePolicyAndAssertEOF();
    }

    @Nonnull public static Policy parse(@Nonnull String sourceText, @Nonnull String origin) {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), null).parsePolicyAndAssertEOF();
    }

    @Nonnull public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin,
        @Nonnull Collection<Notice> warningsOut) {
        return new Parser(Tokeniser.tokenise(sourceText), origin, warningsOut).parsePolicyAndAssertEOF();
    }

    @Nonnull public static Policy parse(@Nonnull String sourceText, @Nonnull String origin,
        @Nonnull Collection<Notice> warningsOut) {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), warningsOut).parsePolicyAndAssertEOF();
    }

    @Nonnull public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull Origin origin) {
        return new Parser(Tokeniser.tokenise(sourceText), origin, null).parsePolicyListAndAssertEOF();
    }

    @Nonnull public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull String origin) {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), null).parsePolicyListAndAssertEOF();
    }

    @Nonnull public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull Origin origin,
        @Nonnull Collection<Notice> warningsOut) {
        return new Parser(Tokeniser.tokenise(sourceText), origin, warningsOut).parsePolicyListAndAssertEOF();
    }

    @Nonnull public static List<Policy> parseMulti(@Nonnull String sourceText, @Nonnull String origin,
        @Nonnull Collection<Notice> warningsOut) {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), warningsOut).parsePolicyListAndAssertEOF();
    }

    @Nonnull private static String trimRHSWS(@Nonnull String s) {
        int i;
        for (i = s.length() - 1; i >= 0; --i) {
            int c = s.codePointAt(i);
            if (!WSP.matcher(new String(new int[]{c}, 0, 1)).find())
                break;
        }
        
        return s.substring(0, i + 1);
    }

    @Nonnull protected Notice createNotice(@Nonnull Notice.Type type, @Nonnull String message) {
        return new Notice(type, message);
    }

    @Nonnull protected Notice createNotice(@Nullable Token token, @Nonnull Notice.Type type, @Nonnull String message) {
        return new Notice(type, message);
    }

    private void warn(@Nullable Token token, @Nonnull String message) {
        if (this.noticesOut != null) {
            this.noticesOut.add(this.createNotice(token, Notice.Type.WARNING, message));
        }
    }

    private void error(@Nullable Token token, @Nonnull String message) {
        if (this.noticesOut != null) {
            this.noticesOut.add(this.createNotice(token, Notice.Type.ERROR, message));
        }
    }

    private void info(@Nullable Token token, @Nonnull String message) {
        if (this.noticesOut != null) {
            this.noticesOut.add(this.createNotice(token, Notice.Type.INFO, message));
        }
    }

    @Nonnull private Token advance() {
        return this.tokens[this.index++];
    }

    protected boolean hasNext() {
        return this.index < this.tokens.length;
    }

    private boolean hasNext(@Nonnull Class<? extends Token> c) {
        return this.hasNext() && c.isAssignableFrom(this.tokens[this.index].getClass());
    }

    private boolean eat(@Nonnull Class<? extends Token> c) {
        if (this.hasNext(c)) {
            this.advance();
            return true;
        }
        return false;
    }

    @Nonnull
    private static List<SubDirectiveValueToken> splitByWSP(@Nonnull Token token) {
        List<SubDirectiveValueToken> tokens = new ArrayList<>();
        @Nullable Location startLocation = token.startLocation;
        if (startLocation == null) {
            for (String s : WSP.split(trimRHSWS(token.value))) {
                tokens.add(new SubDirectiveValueToken(s));
            }
        } else {
            Matcher m = NotWSP.matcher(token.value);
            int offset = 0;
            while (m.find(offset)) {
                SubDirectiveValueToken dv = new SubDirectiveValueToken(token.value.substring(m.start(), m.end()));
                dv.startLocation = new Location(startLocation.line, startLocation.column + m.start(), startLocation.offset + m.start());
                dv.endLocation = new Location(startLocation.line, startLocation.column + m.end(), startLocation.offset + m.end());
                offset = m.end();
                tokens.add(dv);
            }
        }
        return tokens;
    }

    @Nonnull protected DirectiveValueParseException createError(@Nonnull String message) {
        return new DirectiveValueParseException(message);
    }

    @Nonnull protected Policy parsePolicy() {
        Policy policy = new Policy(this.origin);
        while (this.hasNext()) {
            if (this.hasNext(PolicySeparatorToken.class))
                break;
            if (this.eat(DirectiveSeparatorToken.class))
                continue;
            try {
                policy.addDirective(this.parseDirective());
            } catch (DirectiveParseException ignored) {
            }
        }
        return policy;
    }

    @Nonnull protected Policy parsePolicyAndAssertEOF() {
        Policy policy = this.parsePolicy();
        if (this.hasNext()) {
            Token t = this.advance();
            this.error(t, "Expecting end of policy but found " + t.value);
        }
        return policy;
    }

    @Nonnull protected List<Policy> parsePolicyList() {
        List<Policy> policies = new ArrayList<>();
        policies.add(this.parsePolicy());
        while (this.hasNext(PolicySeparatorToken.class)) {
            while (this.eat(PolicySeparatorToken.class))
                ;
            policies.add(this.parsePolicy());
        }
        return policies;
    }

    @Nonnull protected List<Policy> parsePolicyListAndAssertEOF() {
        List<Policy> policies = this.parsePolicyList();
        if (this.hasNext()) {
            Token t = this.advance();
            this.error(t, "Expecting end of policy list but found " + t.value);
        }
        return policies;
    }

    @Nonnull private Directive<?> parseDirective() throws DirectiveParseException {
        if (!this.hasNext(DirectiveNameToken.class)) {
            Token t = this.advance();
            this.error(t, "Expecting directive-name but found " + WSP.split(t.value, 2)[0]);
            throw MISSING_DIRECTIVE_NAME;
        }
        Directive result;
        DirectiveNameToken token = (DirectiveNameToken) this.advance();
        try {
            switch (token.subtype) {
                case BaseUri:
                    result = new BaseUriDirective(this.parseSourceList());
                    break;
                case BlockAllMixedContent:
                    this.warn(token,
                        "The " + token.value + " is an experimental directive that will be likely added to the CSP specification.");
                    this.enforceMissingDirectiveValue(token);
                    result = new BlockAllMixedContentDirective();
                    break;
                case ChildSrc:
                    result = new ChildSrcDirective(this.parseSourceList());
                    break;
                case ConnectSrc:
                    result = new ConnectSrcDirective(this.parseSourceList());
                    break;
                case DefaultSrc:
                    result = new DefaultSrcDirective(this.parseSourceList());
                    break;
                case FontSrc:
                    result = new FontSrcDirective(this.parseSourceList());
                    break;
                case FormAction:
                    result = new FormActionDirective(this.parseSourceList());
                    break;
                case FrameAncestors:
                    result = new FrameAncestorsDirective(this.parseAncestorSourceList());
                    break;
                case ImgSrc:
                    result = new ImgSrcDirective(this.parseSourceList());
                    break;
                case ManifestSrc:
                    this.warn(token,
                        "The " + token.value + " is an experimental directive that will be likely added to the CSP specification.");
                    result = new ManifestSrcDirective(this.parseSourceList());
                    break;
                case MediaSrc:
                    result = new MediaSrcDirective(this.parseSourceList());
                    break;
                case ObjectSrc:
                    result = new ObjectSrcDirective(this.parseSourceList());
                    break;
                case PluginTypes:
                    Set<MediaType> mediaTypes = this.parseMediaTypeList();
                    if (mediaTypes.isEmpty()) {
                        this.error(token, "The media-type-list must contain at least one media-type");
                        throw INVALID_MEDIA_TYPE_LIST;
                    }
                    result = new PluginTypesDirective(mediaTypes);
                    break;
                case Referrer:
                    this.warn(token,
                        "The " + token.value + " is an experimental directive that will be likely added to the CSP specification.");
                    Set<ReferrerValue> referrerTokens = this.parseReferrerTokenList();
                    if (referrerTokens.isEmpty()) {
                        this.error(token, "The referrer directive must contain exactly one referrer-token");
                        throw INVALID_REFERRER_TOKEN_LIST;
                    }
                    result = new ReferrerDirective(referrerTokens);
                    break;
                case ReportUri:
                    Set<URI> uriList = this.parseUriList();
                    if (uriList.isEmpty()) {
                        this.error(token, "The report-uri directive must contain at least one uri-reference");
                        throw INVALID_URI_REFERENCE_LIST;
                    }
                    result = new ReportUriDirective(uriList);
                    break;
                case Sandbox:
                    result = new SandboxDirective(this.parseSandboxTokenList());
                    break;
                case ScriptSrc:
                    result = new ScriptSrcDirective(this.parseSourceList());
                    break;
                case StyleSrc:
                    result = new StyleSrcDirective(this.parseSourceList());
                    break;
                case UpgradeInsecureRequests:
                    this.enforceMissingDirectiveValue(token);
                    result = new UpgradeInsecureRequestsDirective();
                    break;
                case Allow:
                    this.error(token,
                        "The allow directive has been replaced with default-src and is not in the CSP specification.");
                    if (this.hasNext(DirectiveValueToken.class))
                        this.advance();
                    throw INVALID_DIRECTIVE_NAME;
                case FrameSrc:
                    this.warn(token,
                        "The frame-src directive is deprecated as of CSP version 1.1. Authors who wish to govern nested browsing contexts SHOULD use the child-src directive instead.");
                    result = new FrameSrcDirective(this.parseSourceList());
                    break;
                case Options:
                    this.error(token,
                        "The options directive has been replaced with 'unsafe-inline' and 'unsafe-eval' and is not in the CSP specification.");
                    if (this.hasNext(DirectiveValueToken.class))
                        this.advance();
                    throw INVALID_DIRECTIVE_NAME;
                case Unrecognised:
                default:
                    this.error(token, "Unrecognised directive-name: " + token.value);
                    if (this.hasNext(DirectiveValueToken.class))
                        this.advance();
                    throw INVALID_DIRECTIVE_NAME;
            }
        } finally {
            if (this.hasNext(UnknownToken.class)) {
                Token t = this.advance();
                int cp = t.value.codePointAt(0);
                this.error(t, String.format(
                    "Expecting directive-value but found U+%04X (%s). Non-ASCII and non-printable characters must be percent-encoded",
                    cp, new String(new int[] {cp}, 0, 1)));
                throw INVALID_DIRECTIVE_VALUE;
            }
        }
        return result;
    }

    private void enforceMissingDirectiveValue(@Nonnull Token directiveNameToken) throws DirectiveParseException {
        if (this.eat(DirectiveValueToken.class)) {
            this.error(directiveNameToken, "The " + directiveNameToken.value + " directive must not contain any value");
            throw NON_EMPTY_VALUE_TOKEN_LIST;
        }
    }

    @Nonnull private Set<MediaType> parseMediaTypeList() throws DirectiveParseException {
        Set<MediaType> mediaTypes = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    mediaTypes.add(this.parseMediaType(subdv));
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_MEDIA_TYPE_LIST;
            }
        }

        return mediaTypes;
    }

    @Nonnull private MediaType parseMediaType(@Nonnull SubDirectiveValueToken token) throws DirectiveValueParseException {
        Matcher matcher = Constants.mediaTypePattern.matcher(token.value);
        if (matcher.find()) {
            return new MediaType(matcher.group("type"), matcher.group("subtype"));
        }
        this.error(token, "Expecting media-type but found " + token.value);
        throw INVALID_MEDIA_TYPE;
    }

    @Nonnull private Set<SourceExpression> parseSourceList() throws DirectiveParseException {
        Set<SourceExpression> sourceExpressions = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            boolean seenNone = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    SourceExpression se = this.parseSourceExpression(subdv);
                    if (seenNone || se == None.INSTANCE && !sourceExpressions.isEmpty()) {
                        this.error(subdv, "'none' must not be combined with any other source-expression");
                        throw INVALID_SOURCE_EXPR;
                    }
                    if (se == None.INSTANCE) {
                        seenNone = true;
                    }
                    sourceExpressions.add(se);
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_SOURCE_LIST;
            }
        }
        return sourceExpressions;
    }

    @Nonnull private SourceExpression parseSourceExpression(@Nonnull SubDirectiveValueToken token)
        throws DirectiveValueParseException {
        switch (token.value.toLowerCase()) {
            case "'none'":
                return None.INSTANCE;
            case "'self'":
                return KeywordSource.Self;
            case "'unsafe-inline'":
                return KeywordSource.UnsafeInline;
            case "'unsafe-eval'":
                return KeywordSource.UnsafeEval;
            case "'unsafe-redirect'":
                this.warn(token, "'unsafe-redirect' has been removed from CSP as of version 2.0");
                return KeywordSource.UnsafeRedirect;
            case "self":
            case "unsafe-inline":
            case "unsafe-eval":
            case "unsafe-redirect":
            case "none":
                this.warn(token,
                    "This host name is unusual, and likely meant to be a keyword that is missing the required quotes: \'"
                        + token.value.toLowerCase() + "\'");
            default:
                if (token.value.startsWith("'nonce-")) {
                    String nonce = token.value.substring(7, token.value.length() - 1);
                    NonceSource nonceSource = new NonceSource(nonce);
                    nonceSource.validationErrors().forEach(str -> this.warn(token, str));
                    return nonceSource;
                } else if (token.value.toLowerCase().startsWith("'sha")) {
                    HashSource.HashAlgorithm algorithm;
                    switch (token.value.substring(4, 7)) {
                        case "256":
                            algorithm = HashSource.HashAlgorithm.SHA256;
                            break;
                        case "384":
                            algorithm = HashSource.HashAlgorithm.SHA384;
                            break;
                        case "512":
                            algorithm = HashSource.HashAlgorithm.SHA512;
                            break;
                        default:
                            this.error(token, "Unrecognised hash algorithm " + token.value.substring(1, 7));
                            throw INVALID_SOURCE_EXPR;
                    }
                    String value = token.value.substring(8, token.value.length() - 1);
                    // convert url-safe base64 to RFC4648 base64
                    String safeValue = value.replace('-', '+').replace('_', '/');
                    Base64Value base64Value;
                    try {
                        base64Value = new Base64Value(safeValue);
                    } catch (IllegalArgumentException e) {
                        this.error(token, e.getMessage());
                        throw INVALID_SOURCE_EXPR;
                    }
                    // warn if value is not RFC4648
                    if (value.contains("-") || value.contains("_")) {
                        this.warn(token,
                            "Invalid base64-value (characters are not in the base64-value grammar). Consider using RFC4648 compliant base64 encoding implementation");
                    }
                    HashSource hashSource = new HashSource(algorithm, base64Value);
                    try {
                        hashSource.validationErrors();
                    } catch (IllegalArgumentException e) {
                        this.error(token, e.getMessage());
                        throw INVALID_SOURCE_EXPR;
                    }
                    return hashSource;
                } else if (token.value.matches("^" + Constants.schemePart + ":$")) {
                    return new SchemeSource(token.value.substring(0, token.value.length() - 1));
                } else {
                    Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
                    if (matcher.find()) {
                        String scheme = matcher.group("scheme");
                        if (scheme != null)
                            scheme = scheme.substring(0, scheme.length() - 3);
                        String portString = matcher.group("port");
                        int port;
                        if (portString == null) {
                            port = scheme == null ?
                                Constants.EMPTY_PORT :
                                SchemeHostPortTriple.defaultPortForProtocol(scheme);
                        } else {
                            port = portString.equals(":*") ?
                                Constants.WILDCARD_PORT :
                                Integer.parseInt(portString.substring(1));
                        }
                        String host = matcher.group("host");
                        String path = matcher.group("path");
                        return new HostSource(scheme, host, port, path);
                    }
                }
        }
        this.error(token, "Expecting source-expression but found " + token.value);
        throw INVALID_SOURCE_EXPR;
    }

    @Nonnull private Set<AncestorSource> parseAncestorSourceList() throws DirectiveParseException {
        Set<AncestorSource> ancestorSources = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            boolean seenNone = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    AncestorSource as = this.parseAncestorSource(subdv);
                    if (seenNone || as == None.INSTANCE && !ancestorSources.isEmpty()) {
                        this.error(subdv, "The 'none' keyword must not be combined with any other ancestor-source");
                        throw INVALID_ANCESTOR_SOURCE;
                    }
                    if (as == None.INSTANCE) {
                        seenNone = true;
                    }
                    ancestorSources.add(as);
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_ANCESTOR_SOURCE_LIST;
            }
        }
        return ancestorSources;
    }

    @Nonnull private AncestorSource parseAncestorSource(@Nonnull SubDirectiveValueToken token)
        throws DirectiveValueParseException {
        if (token.value.equalsIgnoreCase("'none'")) {
            return None.INSTANCE;
        }
        if (token.value.equalsIgnoreCase("'self'")) {
            return KeywordSource.Self;
        }
        if (token.value.matches("^" + Constants.schemePart + ":$")) {
            return new SchemeSource(token.value.substring(0, token.value.length() - 1));
        } else {
            Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
            if (matcher.find()) {
                String scheme = matcher.group("scheme");
                if (scheme != null)
                    scheme = scheme.substring(0, scheme.length() - 3);
                String portString = matcher.group("port");
                int port;
                if (portString == null) {
                    port = scheme == null ? Constants.EMPTY_PORT : SchemeHostPortTriple.defaultPortForProtocol(scheme);
                } else {
                    port =
                        portString.equals(":*") ? Constants.WILDCARD_PORT : Integer.parseInt(portString.substring(1));
                }
                String host = matcher.group("host");
                String path = matcher.group("path");
                return new HostSource(scheme, host, port, path);
            }
        }
        this.error(token, "Expecting ancestor-source but found " + token.value);
        throw INVALID_ANCESTOR_SOURCE;
    }

    @Nonnull private Set<ReferrerValue> parseReferrerTokenList() throws DirectiveParseException {

        Set<ReferrerValue> referrerTokens = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    ReferrerValue rv = this.parseReferrerToken(subdv);
                    if (!referrerTokens.isEmpty()) {
                        this.error(subdv, "The referrer directive must contain exactly one referrer-token");
                        throw INVALID_REFERRER_TOKEN_LIST;
                    }
                    referrerTokens.add(rv);
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_REFERRER_TOKEN_LIST;
            }
        }
        return referrerTokens;
    }

    @Nonnull private ReferrerValue parseReferrerToken(@Nonnull SubDirectiveValueToken token)
        throws DirectiveValueParseException {
        Matcher matcher = Constants.referrerTokenPattern.matcher(token.value);
        if (matcher.find()) {
            return new ReferrerValue(token.value);
        }
        this.error(token, "Expecting referrer-token but found " + token.value);
        throw INVALID_REFERRER_TOKEN;
    }

    @Nonnull private Set<SandboxValue> parseSandboxTokenList() throws DirectiveParseException {
        Set<SandboxValue> sandboxTokens = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    sandboxTokens.add(this.parseSandboxToken(subdv));
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_SANDBOX_TOKEN_LIST;
            }
        }
        return sandboxTokens;
    }

    @Nonnull private SandboxValue parseSandboxToken(@Nonnull SubDirectiveValueToken token) throws DirectiveValueParseException {
        Matcher matcher = Constants.sandboxEnumeratedTokenPattern.matcher(token.value);
        if (matcher.find()) {
            return new SandboxValue(token.value);
        } else {
            this.warn(token, "The sandbox directive should contain only allow-forms, allow-modals, "
                + "allow-pointer-lock, allow-popups, allow-popups-to-escape-sandbox, "
                + "allow-same-origin, allow-scripts, or allow-top-navigation");
            matcher = Constants.sandboxTokenPattern.matcher(token.value);
            if (matcher.find()) {
                return new SandboxValue(token.value);
            }
        }

        this.error(token, "Expecting sandbox-token but found " + token.value);
        throw INVALID_SANDBOX_TOKEN;
    }

    @Nonnull private Set<URI> parseUriList() throws DirectiveParseException {
        Set<URI> uriList = new LinkedHashSet<>();
        if (this.hasNext(DirectiveValueToken.class)) {
            boolean parseException = false;
            Token dv = this.advance();
            for (SubDirectiveValueToken subdv : splitByWSP(dv)) {
                try {
                    uriList.add(this.parseUri(subdv));
                } catch (DirectiveValueParseException e) {
                    parseException = true;
                }
            }
            if (parseException) {
                throw INVALID_URI_REFERENCE_LIST;
            }
        }
        return uriList;
    }

    @Nonnull private URI parseUri(@Nonnull SubDirectiveValueToken token) throws DirectiveValueParseException {
        try {
            return URI.parseWithOrigin(this.origin, token.value);
        } catch (IllegalArgumentException ignored) {
            this.error(token, "Expecting uri-reference but found " + token.value);
            throw INVALID_URI_REFERENCE;
        }
    }

    private static class SubDirectiveValueToken extends Token {
        protected SubDirectiveValueToken(@Nonnull String value) {
            super(value);
        }

        @Nonnull @Override public String toJSON() {
            return super.toJSON("SubDirectiveValue");
        }
    }

    private static class DirectiveParseException extends Exception {
        @Nullable Location startLocation;
        @Nullable Location endLocation;

        private DirectiveParseException(@Nonnull String message) {
            super(message);
        }

        @Nonnull @Override public String getMessage() {
            if (startLocation == null) {
                return super.getMessage();
            }
            return startLocation.show() + ": " + super.getMessage();
        }
    }

    protected static class DirectiveValueParseException extends Exception {
        @Nullable Location startLocation;
        @Nullable Location endLocation;

        private DirectiveValueParseException(@Nonnull String message) {
            super(message);
        }

        @Nonnull @Override public String getMessage() {
            if (startLocation == null) {
                return super.getMessage();
            }
            return startLocation.show() + ": " + super.getMessage();
        }
    }
}