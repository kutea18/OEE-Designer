package org.point85.app.messaging;

import java.time.OffsetDateTime;

import org.point85.app.AppUtils;
import org.point85.app.charts.DataSubscriber;
import org.point85.domain.messaging.EquipmentEventMessage;
import org.point85.domain.mqtt.MQTTClient;
import org.point85.domain.mqtt.MQTTEquipmentEventListener;
import org.point85.domain.mqtt.MQTTSource;
import org.point85.domain.mqtt.QualityOfService;
import org.point85.domain.script.EventResolver;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;

public class MQTTTrendController extends BaseMessagingTrendController
		implements MQTTEquipmentEventListener, DataSubscriber {
	// MQTT client
	private MQTTClient mqttClient;

	@Override
	public boolean isSubscribed() {
		return mqttClient != null ? true : false;
	}

	@Override
	public void subscribeToDataSource() throws Exception {
		if (mqttClient != null) {
			return;
		}

		// client
		mqttClient = new MQTTClient();

		// MQTT source
		MQTTSource source = (MQTTSource) trendChartController.getEventResolver().getDataSource();

		// connect
		mqttClient.startUp(source.getHost(), source.getPort(), source.getUserName(), source.getUserPassword(), this);

		// add to context
		getApp().getAppContext().addMQTTClient(mqttClient);

		// start the trend
		trendChartController.onStartTrending();
	}

	@Override
	public void unsubscribeFromDataSource() throws Exception {
		if (mqttClient == null) {
			return;
		}
		mqttClient.shutDown();

		// remove from app context
		getApp().getAppContext().removeMQTTClient(mqttClient);
		mqttClient = null;

		// stop the trend
		trendChartController.onStopTrending();
	}

	@Override
	public void onMQTTEquipmentEvent(EquipmentEventMessage message) {
		ResolutionService service = new ResolutionService(message.getSourceId(), message.getValue(),
				message.getDateTime());

		service.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent event) {
				Throwable t = event.getSource().getException();

				if (t != null) {
					// connection failed
					AppUtils.showErrorDialog(t.getMessage());
				}
			}
		});

		// run on application thread
		service.start();
	}

	@FXML
	private void onLoopbackTest() {
		try {
			if (mqttClient == null) {
				throw new Exception("The trend is not connected to an MQTT server.");
			}

			EventResolver eventResolver = trendChartController.getEventResolver();

			String sourceId = eventResolver.getSourceId();
			String value = getLoopbackValue();

			EquipmentEventMessage msg = new EquipmentEventMessage();
			msg.setSourceId(sourceId);
			msg.setValue(value);
			msg.setDateTime(OffsetDateTime.now());

			mqttClient.publish(msg, QualityOfService.AT_MOST_ONCE);
		} catch (Exception e) {
			AppUtils.showErrorDialog(e);
		}
	}
}
