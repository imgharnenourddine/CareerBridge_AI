package com.ai.guild.career_transition_platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeechService {

	private static final String GROQ_TRANSCRIPTIONS_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
	private static final String ASSEMBLYAI_UPLOAD_URL = "https://api.assemblyai.com/v2/upload";
	private static final String ASSEMBLYAI_TRANSCRIPT_URL = "https://api.assemblyai.com/v2/transcript";
	private static final String ELEVENLABS_TTS_TEMPLATE = "https://api.elevenlabs.io/v1/text-to-speech/%s";
	private static final String UNREAL_SPEECH_STREAM_URL = "https://api.v7.unrealspeech.com/stream";

	private final OkHttpClient okHttpClient;
	private final ObjectMapper objectMapper;

	@Value("${groq.api-key}")
	private String groqApiKey;

	@Value("${assemblyai.api-key}")
	private String assemblyAiApiKey;

	@Value("${elevenlabs.api-key}")
	private String elevenLabsApiKey;

	@Value("${elevenlabs.voice-id}")
	private String elevenLabsVoiceId;

	@Value("${unreal-speech.api-key}")
	private String unrealSpeechApiKey;

	/**
	 * Speech-to-text with fallback: Groq Whisper → AssemblyAI → null (browser Web Speech API).
	 *
	 * @return transcribed text, or null if all backends failed (client should use Web Speech API)
	 */
	public String transcribe(byte[] audioBytes, String filename, String contentType) {
		if (audioBytes == null || audioBytes.length == 0) {
			log.warn("transcribe: empty audio payload");
			return null;
		}
		String safeName = (filename != null && !filename.isBlank()) ? filename : "audio.webm";
		log.debug("transcribe: attempting Groq Whisper, audioBytes size={}", audioBytes.length);

		String groq = transcribeWithGroq(audioBytes, safeName, contentType);
		if (groq != null) {
			return groq;
		}
		log.warn("transcribe: Groq Whisper failed, falling back to AssemblyAI");

		String assembly = transcribeWithAssemblyAi(audioBytes, safeName);
		if (assembly != null) {
			return assembly;
		}
		log.warn("transcribe: AssemblyAI failed, falling back to browser Web Speech API (returning null)");

		log.error("transcribe: all STT backends failed; client must use Web Speech API");
		return null;
	}

	private String transcribeWithGroq(byte[] audioBytes, String filename, String contentType) {
		log.info("STT using API: Groq Whisper (model=whisper-large-v3)");
		MediaType mediaType = MediaType.parse(contentType != null && !contentType.isBlank() ? contentType : "application/octet-stream");
		RequestBody fileBody = RequestBody.create(audioBytes, mediaType);
		MultipartBody body = new MultipartBody.Builder()
				.setType(MultipartBody.FORM)
				.addFormDataPart("model", "whisper-large-v3")
				.addFormDataPart("file", filename, fileBody)
				.build();

		Request request = new Request.Builder()
				.url(GROQ_TRANSCRIPTIONS_URL)
				.header("Authorization", "Bearer " + groqApiKey)
				.post(body)
				.build();

		try (Response response = okHttpClient.newCall(request).execute()) {
			String respBody = response.body() != null ? response.body().string() : "";
			if (!response.isSuccessful()) {
				log.warn("Groq Whisper failure: status={} reason={} body={}", response.code(), response.message(), truncate(respBody));
				return null;
			}
			JsonNode root = objectMapper.readTree(respBody);
			String text = root.path("text").asText(null);
			if (text == null || text.isBlank()) {
				log.warn("Groq Whisper returned empty text: {}", truncate(respBody));
				return null;
			}
			log.info("STT Groq Whisper success: text length={}", text.length());
			return text.trim();
		} catch (IOException e) {
			log.warn("Groq Whisper IO error: {}", e.getMessage());
			return null;
		}
	}

	private String transcribeWithAssemblyAi(byte[] audioBytes, String filename) {
		log.info("STT using API: AssemblyAI (upload + poll)");
		try {
			RequestBody uploadBody = RequestBody.create(audioBytes, MediaType.parse("application/octet-stream"));
			Request uploadReq = new Request.Builder()
					.url(ASSEMBLYAI_UPLOAD_URL)
					.header("Authorization", assemblyAiApiKey)
					.post(uploadBody)
					.build();

			String uploadUrl;
			try (Response uploadRes = okHttpClient.newCall(uploadReq).execute()) {
				String uploadJson = uploadRes.body() != null ? uploadRes.body().string() : "";
				if (!uploadRes.isSuccessful()) {
					log.warn("AssemblyAI upload failure: status={} body={}", uploadRes.code(), truncate(uploadJson));
					return null;
				}
				uploadUrl = objectMapper.readTree(uploadJson).path("upload_url").asText(null);
				if (uploadUrl == null || uploadUrl.isBlank()) {
					log.warn("AssemblyAI upload missing upload_url: {}", truncate(uploadJson));
					return null;
				}
			}

			String createJson = objectMapper.createObjectNode().put("audio_url", uploadUrl).toString();
			Request createReq = new Request.Builder()
					.url(ASSEMBLYAI_TRANSCRIPT_URL)
					.header("Authorization", assemblyAiApiKey)
					.header("Content-Type", "application/json")
					.post(RequestBody.create(createJson, MediaType.parse("application/json")))
					.build();

			String transcriptId;
			try (Response createRes = okHttpClient.newCall(createReq).execute()) {
				String createBody = createRes.body() != null ? createRes.body().string() : "";
				if (!createRes.isSuccessful()) {
					log.warn("AssemblyAI transcript create failure: status={} body={}", createRes.code(), truncate(createBody));
					return null;
				}
				transcriptId = objectMapper.readTree(createBody).path("id").asText(null);
				if (transcriptId == null || transcriptId.isBlank()) {
					log.warn("AssemblyAI transcript create missing id: {}", truncate(createBody));
					return null;
				}
			}

			String pollUrl = ASSEMBLYAI_TRANSCRIPT_URL + "/" + transcriptId;
			for (int i = 0; i < 120; i++) {
				Thread.sleep(1000);
				Request pollReq = new Request.Builder()
						.url(pollUrl)
						.header("Authorization", assemblyAiApiKey)
						.get()
						.build();
				try (Response pollRes = okHttpClient.newCall(pollReq).execute()) {
					String pollBody = pollRes.body() != null ? pollRes.body().string() : "";
					if (!pollRes.isSuccessful()) {
						log.warn("AssemblyAI poll failure: status={} body={}", pollRes.code(), truncate(pollBody));
						return null;
					}
					JsonNode node = objectMapper.readTree(pollBody);
					String status = node.path("status").asText("").toLowerCase(Locale.ROOT);
					if ("completed".equals(status)) {
						String text = node.path("text").asText(null);
						if (text == null || text.isBlank()) {
							log.warn("AssemblyAI completed with empty text");
							return null;
						}
						log.info("STT AssemblyAI success: text length={}", text.length());
						return text.trim();
					}
					if ("error".equals(status)) {
						log.warn("AssemblyAI transcript error: {}", truncate(pollBody));
						return null;
					}
				}
			}
			log.warn("AssemblyAI polling timed out for transcriptId={}", transcriptId);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("AssemblyAI interrupted: {}", e.getMessage());
			return null;
		} catch (IOException e) {
			log.warn("AssemblyAI IO error: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Text-to-speech with fallback: ElevenLabs → Unreal Speech → null (browser Web Speech API).
	 */
	public SpeechAudio synthesize(String text) {
		if (text == null || text.isBlank()) {
			log.warn("synthesize: empty text");
			return null;
		}
		log.debug("synthesize: text length={}", text.length());

		SpeechAudio eleven = synthesizeElevenLabs(text);
		if (eleven != null) {
			return eleven;
		}
		log.warn("synthesize: ElevenLabs failed, falling back to Unreal Speech");

		SpeechAudio unreal = synthesizeUnrealSpeech(text);
		if (unreal != null) {
			return unreal;
		}
		log.warn("synthesize: Unreal Speech failed, falling back to browser Web Speech API (returning null)");

		log.error("synthesize: all TTS backends failed; client must use Web Speech API");
		return null;
	}

	private SpeechAudio synthesizeElevenLabs(String text) {
		log.info("TTS using API: ElevenLabs (voiceId={})", elevenLabsVoiceId);
		String url = String.format(Locale.ROOT, ELEVENLABS_TTS_TEMPLATE, elevenLabsVoiceId);
		String json = Objects.requireNonNull(
				objectMapper.createObjectNode()
						.put("text", text)
						.put("model_id", "eleven_multilingual_v2")
						.toString()
		);
		Request request = new Request.Builder()
				.url(url)
				.header("xi-api-key", elevenLabsApiKey)
				.header("Accept", "audio/mpeg")
				.header("Content-Type", "application/json")
				.post(RequestBody.create(json, MediaType.parse("application/json")))
				.build();

		try (Response response = okHttpClient.newCall(request).execute()) {
			byte[] bytes = response.body() != null ? response.body().bytes() : new byte[0];
			if (!response.isSuccessful()) {
				String err = new String(bytes);
				log.warn("ElevenLabs TTS failure: status={} reason={} body={}", response.code(), response.message(), truncate(err));
				return null;
			}
			log.info("TTS ElevenLabs success: audio bytes size={}", bytes.length);
			log.debug("TTS ElevenLabs audio bytes size={}", bytes.length);
			return new SpeechAudio(bytes, "audio/mpeg");
		} catch (IOException e) {
			log.warn("ElevenLabs TTS IO error: {}", e.getMessage());
			return null;
		}
	}

	private SpeechAudio synthesizeUnrealSpeech(String text) {
		log.info("TTS using API: Unreal Speech (stream)");
		String json = Objects.requireNonNull(
				objectMapper.createObjectNode()
						.put("Text", text)
						.put("VoiceId", "Scarlett")
						.put("Bitrate", "192k")
						.put("OutputFormat", "mp3")
						.toString()
		);
		Request request = new Request.Builder()
				.url(UNREAL_SPEECH_STREAM_URL)
				.header("Authorization", "Bearer " + unrealSpeechApiKey)
				.header("Content-Type", "application/json")
				.post(RequestBody.create(json, MediaType.parse("application/json")))
				.build();

		try (Response response = okHttpClient.newCall(request).execute()) {
			byte[] bytes = response.body() != null ? response.body().bytes() : new byte[0];
			if (!response.isSuccessful()) {
				String err = new String(bytes);
				log.warn("Unreal Speech TTS failure: status={} reason={} body={}", response.code(), response.message(), truncate(err));
				return null;
			}
			String ct = response.header("Content-Type", "audio/mpeg");
			log.info("TTS Unreal Speech success: audio bytes size={}", bytes.length);
			log.debug("TTS Unreal Speech audio bytes size={}", bytes.length);
			return new SpeechAudio(bytes, ct != null ? ct : "audio/mpeg");
		} catch (IOException e) {
			log.warn("Unreal Speech TTS IO error: {}", e.getMessage());
			return null;
		}
	}

	private static String truncate(String s) {
		if (s == null) {
			return "";
		}
		return s.length() > 500 ? s.substring(0, 500) + "..." : s;
	}

	public record SpeechAudio(byte[] audioBytes, String contentType) {
	}
}
