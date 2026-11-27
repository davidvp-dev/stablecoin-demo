package com.stablecoin.chainservice.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stablecoin.chainservice.service.MintSender;
import com.stablecoin.chainservice.util.Constants;

/**
 * Consumes MintApproved from mint-orders.v1 (group chain-service).
 *
 * One consumer thread, so mints are sent one at a time; that is what lets the
 * nonce come from the node (IMPLEMENTATION_PLAN_V1, "Nonce management"). The
 * offset is committed after this method returns; if it throws, the error
 * handler in KafkaConfig redelivers the message.
 */
@Component
public class MintApprovedListener {

	private final MintSender mintSender;

	public MintApprovedListener(MintSender mintSender) {
		this.mintSender = mintSender;
	}

	@KafkaListener(topics = Constants.MINT_ORDERS_TOPIC)
	public void onMintApproved(ConsumerRecord<String, String> record) {
		mintSender.handleMintApproved(record.value());
	}
}
