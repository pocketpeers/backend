package com.pocketpeers.backend.operations.infrastructure.twilio;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class TwilioSmsService {

    @Value("${twilio.fromPhone}")
    private  String FROM_PHONE;

    public TwilioSmsService(
            @Value("${twilio.accountSID}")
            String ACCOUNT_SID,
            @Value("${twilio.authToken}")
            String AUTH_TOKEN
    ) {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
    }


    private String formatPhoneNumber(String phoneNumber) {

        if (!phoneNumber.startsWith("+")) {

            phoneNumber = "+51" + phoneNumber;
        }
        return phoneNumber;
    }


    public void sendReminder(String toPhoneNumber, String dueDate) {

        String formattedPhoneNumber = formatPhoneNumber(toPhoneNumber);
        String messageBody = String.format("Recordatorio:");

        Message.creator(
                new PhoneNumber(formattedPhoneNumber),
                new PhoneNumber(FROM_PHONE),
                messageBody
        ).create();
    }

    public void sendGeneralNotification(String toPhoneNumber, String messageBody) {

        String formattedPhoneNumber = formatPhoneNumber(toPhoneNumber);


        try{
            Message.creator(
                    new PhoneNumber(formattedPhoneNumber),
                    new PhoneNumber(FROM_PHONE),
                    messageBody
            ).create();
            System.out.println("SMS sent successfully to " + formattedPhoneNumber);
        }
        catch (Exception e) {
            System.err.println("Failed to send SMS to " + formattedPhoneNumber + ": " + e.getMessage());
        }


    }
}
