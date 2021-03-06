package com.maigo.cloud.service;

import com.maigo.cloud.dao.MessageDAO;
import com.maigo.cloud.model.Message;
import com.maigo.cloud.model.User;
import com.maigo.cloud.network.Session;
import com.maigo.cloud.xmpp.IQMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageService
{
    private UserService userService;
    private SessionService sessionService;

    private MessageDAO messageDAO;

    /**
     * @key stanzaId in IQMessage
     * @value id in Message Table generated by Hibernate
     */
    private Map<String, String> pushMessageIdsMap = new ConcurrentHashMap<String, String>();

    public void setUserService(UserService userService)
    {
        this.userService = userService;
    }

    public void setSessionService(SessionService sessionService)
    {
        this.sessionService = sessionService;
    }

    public void setMessageDAO(MessageDAO messageDAO)
    {
        this.messageDAO = messageDAO;
    }

    /**
     * send a message to a user. The message will be saved in the database and
     * wait for a ack to confirm. The un-confirm messages will be send to the user
     * again when the user login at the next time.
     * @param message
     * @return if the user to send is exist
     */
    public boolean sendMessage(Message message)
    {
        String username = message.getUsername();
        User user = userService.getUserByUsername(username);
        if(user == null)
            return false;

        String messageId = messageDAO.addMessage(message);

        Session session = sessionService.getSessionBindWithUser(user);
        if(session != null)
        {
            String stanzaId = getRandomStanzaID();
            pushMessageIdsMap.put(stanzaId, messageId);

            IQMessage iqMessage = new IQMessage();
            iqMessage.setId(stanzaId);
            iqMessage.setType("set");

            iqMessage.setTitle(message.getTitle());
            iqMessage.setContent(message.getContent());

            session.sendStanza(iqMessage);
        }

        return true;
    }

    /**
     * send the messages stored while the user is offline. The message will not be
     * saved into database since it is already there.
     * @param user
     */
    public void sendOfflineMessagesToUser(User user)
    {
        Session session = sessionService.getSessionBindWithUser(user);
        if(session == null)
            return;

        List<Message> messageList = messageDAO.getOfflineMessageList(user);
        for(Message message : messageList)
        {
            String stanzaId = getRandomStanzaID();
            pushMessageIdsMap.put(stanzaId, String.valueOf(message.getId()));

            IQMessage iqMessage = new IQMessage();
            iqMessage.setId(stanzaId);
            iqMessage.setType("set");
            iqMessage.setTitle(message.getTitle());
            iqMessage.setContent(message.getContent());

            session.sendStanza(iqMessage);
        }

        System.out.println("[Debug]MessageService: send " + messageList.size() + " off-line messages to user " +
                user.getUsername());
    }

    public void confirmMessage(String stanzaId)
    {
        String messageId = pushMessageIdsMap.get(stanzaId);
        if(messageId == null)
            return;

        Message message = messageDAO.getMessage(messageId);
        message.setConfirmed(true);
        messageDAO.updateMessage(message);

        System.out.println("[Debug]MessageService: confirm Message title = " + message.getTitle()
                + " content = " + message.getContent());
    }

    private String getRandomStanzaID()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
