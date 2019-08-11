package com.mishiranu.dashchan.chan.neochan;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Base64;

import chan.content.ApiException;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class NeochanChanPerformer extends ChanPerformer {
	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException, InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		if (data.isCatalog()) {
			Uri uri = locator.buildPath(data.boardName, "catalog.json");
			JSONArray jsonArray = new HttpRequest(uri, data).read().getJsonArray();
			if (jsonArray == null) {
				throw new InvalidResponseException();
			}
			if (jsonArray.length() == 1) {
				try {
					JSONObject jsonObject = jsonArray.getJSONObject(0);
					if (!jsonObject.has("threads")) {
						return null;
					}
				} catch (JSONException e) {
					throw new InvalidResponseException(e);
				}
			}
			try {
				ArrayList<Posts> threads = new ArrayList<>();
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONArray threadsArray = jsonArray.getJSONObject(i).getJSONArray("threads");
					for (int j = 0; j < threadsArray.length(); j++) {
						threads.add(NeochanModelMapper.createThread(threadsArray.getJSONObject(j),
								locator, data.boardName, true));
					}
				}
				return new ReadThreadsResult(threads);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		} else {
			Uri uri = locator.buildPath(data.boardName, data.pageNumber + ".json");
			JSONObject jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
			if (jsonObject == null) {
				throw new InvalidResponseException();
			}
			if (data.pageNumber == 0) {
				uri = locator.buildQuery("settings.php", "board", data.boardName);
				JSONObject boardConfigObject = new HttpRequest(uri, data).read().getJsonObject();
				if (boardConfigObject != null) {
					NeochanChanConfiguration configuration = NeochanChanConfiguration.get(this);
					configuration.updateFromBoardJson(data.boardName, boardConfigObject, true);
				}
			}
			try {
				JSONArray threadsArray = jsonObject.getJSONArray("threads");
				Posts[] threads = new Posts[threadsArray.length()];
				for (int i = 0; i < threads.length; i++) {
					threads[i] = NeochanModelMapper.createThread(threadsArray.getJSONObject(i),
							locator, data.boardName, false);
				}
				return new ReadThreadsResult(threads);
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
		if (jsonObject != null) {
			try {
				JSONArray jsonArray = jsonObject.getJSONArray("posts");
				if (jsonArray.length() > 0) {
					Post[] posts = new Post[jsonArray.length()];
					for (int i = 0; i < posts.length; i++) {
						posts[i] = NeochanModelMapper.createPost(jsonArray.getJSONObject(i),
								locator, data.boardName);
					}
					return new ReadPostsResult(posts);
				}
				return null;
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadSearchPostsResult onReadSearchPosts(ReadSearchPostsData data) throws HttpException,
			InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildQuery("search.php", "board", data.boardName, "search", data.searchQuery);
		String responseText = new HttpRequest(uri, data).read().getString();
		try {
			return new ReadSearchPostsResult(new NeochanSearchParser(responseText, this).convertPosts());
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
	}

	private ArrayList<BoardCategory> readBoards(HttpRequest.Preset preset, boolean user)
			throws HttpException, InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildPath("");
		String responseText = new HttpRequest(uri, preset).read().getString();
		LinkedHashMap<String, String> officialBoards;
		try {
			officialBoards = new NeochanBoardsParser(responseText).convertMap();
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		}
		uri = locator.buildPath("boards.json");
		JSONArray jsonArray = new HttpRequest(uri, preset).read().getJsonArray();
		if (jsonArray == null) {
			throw new InvalidResponseException();
		}
		try {
			LinkedHashMap<String, ArrayList<Board>> boards = new LinkedHashMap<>();
			if (!user) {
				// Set boards map order
				for (String category : officialBoards.values()) {
					boards.put(category, null);
				}
			}
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String boardName = CommonUtils.getJsonString(jsonObject, "uri");
				if (officialBoards.containsKey(boardName) != user) {
					String category = user ? null : officialBoards.get(boardName);
					ArrayList<Board> boardList = boards.get(category);
					if (boardList == null) {
						boardList = new ArrayList<>();
						boards.put(category, boardList);
					}
					String title = CommonUtils.getJsonString(jsonObject, "title");
					String description = CommonUtils.optJsonString(jsonObject, "subtitle");
					boardList.add(new Board(boardName, title, description));
				}
			}
			ArrayList<BoardCategory> categories = new ArrayList<>();
			for (LinkedHashMap.Entry<String, ArrayList<Board>> entry : boards.entrySet()) {
				if (entry.getValue() != null) {
					categories.add(new BoardCategory(entry.getKey(), entry.getValue()));
				}
			}
			return categories;
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
	}

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) throws HttpException, InvalidResponseException {


		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildPath("boards.json");
		JSONArray jsonArray = new HttpRequest(uri, data).read().getJsonArray();

		try
		{
				Board[] boards = new Board[jsonArray.length()];
				for (int i = 0; i < jsonArray.length(); i++)
				{
					JSONObject boardJsonObject = jsonArray.getJSONObject(i);
					String boardName = CommonUtils.getJsonString(boardJsonObject, "uri");
					String title = CommonUtils.getJsonString(boardJsonObject, "title");
					boards[i] = new Board(boardName, title);
				}
				NeochanChanConfiguration configuration = NeochanChanConfiguration.get(this);
				//configuration.updateFromBoardsJson(jsonArray);
				return new ReadBoardsResult(new BoardCategory("Доски", boards));
		}
		catch (JSONException e) {
			throw new InvalidResponseException(e);
		}




	}




	@Override
	public ReadUserBoardsResult onReadUserBoards(ReadUserBoardsData data) throws HttpException,
			InvalidResponseException {
		ArrayList<BoardCategory> categories = readBoards(data, true);
		if (categories.size() >= 1) {
			return new ReadUserBoardsResult(categories.get(0).getBoards());
		}
		return null;
	}

	@Override
	public ReadPostsCountResult onReadPostsCount(ReadPostsCountData data) throws HttpException,
			InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "res", data.threadNumber + ".json");
		JSONObject jsonObject = new HttpRequest(uri, data).setValidator(data.validator).read().getJsonObject();
		if (jsonObject != null) {
			try {
				return new ReadPostsCountResult(jsonObject.getJSONArray("posts").length());
			} catch (JSONException e) {
				throw new InvalidResponseException(e);
			}
		}
		throw new InvalidResponseException();
	}

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
		if (data.uri.getPath().contains("/thumb/")) {
			// Try all possible extensions for thumbnails
			String path = data.uri.getEncodedPath();
			Uri.Builder builder = data.uri.buildUpon();
			String[] extensions = {".jpg", ".png", ".gif"};

			for (String extension : extensions) {
				try {
					Uri uri = builder.encodedPath(path + extension).build();
					return new ReadContentResult(new HttpRequest(uri, data).read());
				} catch (HttpException e) {
					if (e.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
						throw e;
					}
				}
			}
			throw HttpException.createNotFoundException();
		}
		return super.onReadContent(data);
	}

	private static final Pattern PATTERN_CAPTCHA = Pattern.compile("<img src=\"data:image/png;base64,(.*?)\"");

	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildQuery("settings.php", "board", data.boardName);
		JSONObject jsonObject = new HttpRequest(uri, data).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		try {
			boolean newThreadCaptcha = jsonObject.optBoolean("new_thread_capt");
			jsonObject = jsonObject.getJSONObject("captcha");
			if (jsonObject.getBoolean("enabled") || data.threadNumber == null && newThreadCaptcha) {
				String extra = CommonUtils.getJsonString(jsonObject, "extra");
				Uri providerUri = Uri.parse(CommonUtils.getJsonString(jsonObject, "provider_get"));
				uri = providerUri.buildUpon().scheme(uri.getScheme()).authority(uri.getAuthority())
						.appendQueryParameter("mode", "get").appendQueryParameter("extra", extra)
						.appendQueryParameter("board", data.boardName).build();
				String responseText = new HttpRequest(uri, data).read().getString();
				String challenge = data.holder.getCookieValue("captcha_" + data.boardName);
				Matcher matcher = PATTERN_CAPTCHA.matcher(responseText);
				if (matcher.find() && challenge != null) {
					String base64 = matcher.group(1);
					byte[] imageArray = Base64.decode(base64, Base64.DEFAULT);
					Bitmap image = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
					if (image != null) {
						Bitmap newImage = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
								Bitmap.Config.ARGB_8888);
						Paint paint = new Paint();
						float[] colorMatrixArray = {0.3f, 0.3f, 0.3f, 0f, 48f, 0.3f, 0.3f, 0.3f, 0f, 48f,
								0.3f, 0.3f, 0.3f, 0f, 48f, 0f, 0f, 0f, 1f, 0f};
						paint.setColorFilter(new ColorMatrixColorFilter(colorMatrixArray));
						new Canvas(newImage).drawBitmap(image, 0f, 0f, paint);
						image.recycle();
						CaptchaData captchaData = new CaptchaData();
						captchaData.put(CaptchaData.CHALLENGE, challenge);
						return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(newImage)
								.setValidity(NeochanChanConfiguration.Captcha.Validity.SHORT_LIFETIME);
					}
				}
				throw new InvalidResponseException();
			}
		} catch (JSONException e) {
			throw new InvalidResponseException(e);
		}
		return new ReadCaptchaResult(CaptchaState.SKIP, null);
	}

	@Override
	public SendPostResult onSendPost(SendPostData data) throws HttpException, ApiException, InvalidResponseException {
		MultipartEntity entity = new MultipartEntity();
		entity.add("post", "on");
		entity.add("board", data.boardName);
		entity.add("thread", data.threadNumber);
		entity.add("subject", data.subject);
		entity.add("body", StringUtils.emptyIfNull(data.comment));
		entity.add("name", data.name);
		entity.add("email", data.email);
		entity.add("password", data.password);
		if (data.optionSage) {
			entity.add("no-bump", "on");
		}
		entity.add("user_flag", data.userIcon);
		boolean hasSpoilers = false;
		if (data.attachments != null) {
			for (int i = 0; i < data.attachments.length; i++) {
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file" + (i > 0 ? i + 1 : ""));
				hasSpoilers |= attachment.optionSpoiler;
			}
		}
		if (hasSpoilers) {
			entity.add("spoiler", "on");
		}
		String captchaCookie = null;
		if (data.captchaData != null && data.captchaData.get(CaptchaData.CHALLENGE) != null) {
			captchaCookie = StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.CHALLENGE));
			entity.add("captcha_text", StringUtils.emptyIfNull(data.captchaData.get(CaptchaData.INPUT)));
		}
		entity.add("json_response", "1");

		NeochanChanLocator locator = NeochanChanLocator.get(this);
		Uri uri = locator.buildPath("post.php");
		JSONObject jsonObject = new HttpRequest(uri, data).setPostMethod(entity)
				.addHeader("Referer", (data.threadNumber == null ? locator.createBoardUri(data.boardName, 0)
				: locator.createThreadUri(data.boardName, data.threadNumber)).toString())
				.addCookie("captcha_" + data.boardName, captchaCookie)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}

		String redirect = jsonObject.optString("redirect");
		if (!StringUtils.isEmpty(redirect)) {
			uri = locator.buildPath(redirect);
			String threadNumber = locator.getThreadNumber(uri);
			String postNumber = locator.getPostNumber(uri);
			return new SendPostResult(threadNumber, postNumber);
		}
		String errorMessage = jsonObject.optString("error");
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("CAPTCHA") ||
					errorMessage.contains("You seem to have mistyped the verification") ||
					errorMessage.contains("Você errou o codigo de verificação") ||
					errorMessage.contains("Вы ошиблись при вводе капчи")) {
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			} else if (errorMessage.contains("The body was too short or empty") ||
					errorMessage.contains("O corpo do texto") ||
					errorMessage.contains("Вы ничего не ввели в сообщении")) {
				errorType = ApiException.SEND_ERROR_EMPTY_COMMENT;
			} else if (errorMessage.contains("You must upload an image") ||
					errorMessage.contains("Você deve postar com uma imagem") ||
					errorMessage.contains("Вы должны загрузить изображение")) {
				errorType = ApiException.SEND_ERROR_EMPTY_FILE;
			} else if (errorMessage.contains("The file was too big") || errorMessage.contains("is longer than") ||
					errorMessage.contains("Seu arquivo é grande demais") || errorMessage.contains("é maior que") ||
					errorMessage.contains("Этот файл слишком большой") || errorMessage.contains("не дольше чем")) {
				errorType = ApiException.SEND_ERROR_FILE_TOO_BIG;
			} else if (errorMessage.contains("attempted to upload too many images") ||
					errorMessage.contains("Você tentou fazer upload de muitas") ||
					errorMessage.contains("загрузить слишком много изображений")) {
				errorType = ApiException.SEND_ERROR_FILES_TOO_MANY;
			} else if (errorMessage.contains("was too long") ||
					errorMessage.contains("longo demais") ||
					errorMessage.contains("слишком длинное") || errorMessage.contains("очень большое")) {
				errorType = ApiException.SEND_ERROR_FIELD_TOO_LONG;
			} else if (errorMessage.contains("Thread locked") ||
					errorMessage.contains("Tópico trancado") ||
					errorMessage.contains("Тред закрыт")) {
				errorType = ApiException.SEND_ERROR_CLOSED;
			} else if (errorMessage.contains("Invalid board") ||
					errorMessage.contains("Board inválida") ||
					errorMessage.contains("Неверная доска")) {
				errorType = ApiException.SEND_ERROR_NO_BOARD;
			} else if (errorMessage.contains("Thread specified does not exist") ||
					errorMessage.contains("O tópico especificado não existe") ||
					errorMessage.contains("Данного треда не существует")) {
				errorType = ApiException.SEND_ERROR_NO_THREAD;
			} else if (errorMessage.contains("Unsupported file format") ||
					errorMessage.contains("Formato de arquivo não aceito") ||
					errorMessage.contains("Формат файла не поддерживается")) {
				errorType = ApiException.SEND_ERROR_FILE_NOT_SUPPORTED;
			} else if (errorMessage.contains("That file") ||
					errorMessage.contains("O arquivo") ||
					errorMessage.contains("Этот файл")) {
				errorType = ApiException.SEND_ERROR_FILE_EXISTS;
			} else if (errorMessage.contains("Flood detected") ||
					errorMessage.contains("Flood detectado") ||
					errorMessage.contains("Обнаружен флуд")) {
				errorType = ApiException.SEND_ERROR_TOO_FAST;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			CommonUtils.writeLog("neochan send message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("delete", "on", "board", data.boardName,
				"password", data.password, "json_response", "1");
		for (String postNumber : data.postNumbers) {
			entity.add("delete_" + postNumber, "on");
		}
		if (data.optionFilesOnly) {
			entity.add("file", "on");
		}
		Uri uri = locator.buildPath("post.php");
		JSONObject jsonObject = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).setSuccessOnly(false)
				.read().getJsonObject();
		if (jsonObject == null) {
			throw new InvalidResponseException();
		}
		if (jsonObject.optBoolean("success")) {
			return null;
		}
		String errorMessage = jsonObject.optString("error");
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("Wrong password") ||
					errorMessage.contains("Senha incorreta") ||
					errorMessage.contains("Неверный пароль")) {
				errorType = ApiException.DELETE_ERROR_PASSWORD;
			} else if (errorMessage.contains("before deleting that") ||
					errorMessage.contains("antes de apagar isso") ||
					errorMessage.contains("перед удалением сообщения")) {
				errorType = ApiException.DELETE_ERROR_TOO_NEW;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			}
			CommonUtils.writeLog("neochan delete message", errorMessage);
			throw new ApiException(errorMessage);
		}
		throw new InvalidResponseException();
	}

	private static final Pattern PATTERN_REPORT = Pattern.compile("<strong>(.*?)</strong>");

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data) throws HttpException, ApiException,
			InvalidResponseException {
		String postNumber = data.postNumbers.get(0);
		NeochanChanLocator locator = NeochanChanLocator.get(this);
		UrlEncodedEntity entity = new UrlEncodedEntity("report", "1", "board", data.boardName);
		entity.add("delete_" + postNumber, "1");
		entity.add("reason", StringUtils.emptyIfNull(data.comment));
		if (data.options.contains("global")) {
			entity.add("global", "1");
		}
		Uri uri = locator.buildPath("post.php");
		String responseText = new HttpRequest(uri, data).setPostMethod(entity)
				.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).setSuccessOnly(false)
				.read().getString();
		Matcher matcher = PATTERN_REPORT.matcher(responseText);
		if (matcher.find()) {
			String errorMessage = matcher.group(1);
			if (errorMessage != null) {
				CommonUtils.writeLog("neochan report message", errorMessage);
				throw new ApiException(errorMessage);
			}
			throw new InvalidResponseException();
		}
		return null;
	}
}
