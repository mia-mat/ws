package ws.mia.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
	public static String MONGO_URI = System.getenv("MONGO_URI");
	public static String MONGO_DATABASE = System.getenv("MONGO_DATABASE");
	public static String MONGO_MESSAGE_COLLECTION = System.getenv("MONGO_MESSAGE_COLLECTION");

	private boolean active;

	public DatabaseService() {
		active = false;

		if(MONGO_URI == null) {
			throw new RuntimeException("Missing MongoDB URI env-var, shutting down database service.");
		}

		if(MONGO_DATABASE == null) {
			throw new RuntimeException("Missing MongoDB Database env-var, shutting down database service.");
		}

		if(MONGO_MESSAGE_COLLECTION == null) {
			throw new RuntimeException("Missing MongoDB Message Collection env-var, shutting down database service.");
		}

		active = true;
	}

	public void insertMessage(String address, String name, String email, String message) {
		if(!active) {
			throw new RuntimeException("Tried to use inactive database service");
		}

		try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
			MongoDatabase database = mongoClient.getDatabase(MONGO_DATABASE);
			MongoCollection<Document> collection = database.getCollection(MONGO_MESSAGE_COLLECTION);

			Document doc = new Document("address", address)
					.append("name", name)
					.append("email", email)
					.append("message", message);

			collection.insertOne(doc);
		} catch (Exception e) {
			throw new RuntimeException("Failed to insert document");
		}
	}

}
