package org.waterproofingdata.wpdauth.service;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.waterproofingdata.wpdauth.exception.CustomException;
import org.waterproofingdata.wpdauth.model.ForgotPasswordKeys;
import org.waterproofingdata.wpdauth.model.ForgotPasswordQuestions;
import org.waterproofingdata.wpdauth.model.ForgotPasswordQuestionsUsersAnswers;
import org.waterproofingdata.wpdauth.model.Users;
import org.waterproofingdata.wpdauth.repository.ForgotPasswordKeysRepository;
import org.waterproofingdata.wpdauth.repository.ForgotPasswordQuestionsRepository;
import org.waterproofingdata.wpdauth.repository.ForgotPasswordQuestionsUsersAnswersRepository;
import org.waterproofingdata.wpdauth.repository.UsersRepository;
import org.waterproofingdata.wpdauth.security.JwtTokenProvider;

@Service
public class ForgotPasswordService {
	@Autowired
	private ForgotPasswordKeysRepository forgotPasswordKeysRepository;
	
	@Autowired
	private ForgotPasswordQuestionsRepository forgotPasswordQuestionsRepository;
	
	@Autowired
	private ForgotPasswordQuestionsUsersAnswersRepository forgotPasswordQuestionsUsersAnswersRepository;
	
	@Autowired
	private UsersRepository usersRepository;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
    @Autowired
    private JavaMailSender mailSender;	
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
	public void sendKeyByEmail(String email) {
		Users user = usersRepository.findByEmail(email);
	    if (user == null) {
		      throw new CustomException("The user email doesn't exist", HttpStatus.NOT_FOUND);
	    }		
		
		Random rand = new Random();
		String key = String.format("%04d", rand.nextInt(10000));
		//System.out.printf("%04d%n", rand.nextInt(10000));
		
		ForgotPasswordKeys entity = new ForgotPasswordKeys();
		entity.setEmail(email);
		entity.setKey(key);
		forgotPasswordKeysRepository.save(entity);
		
        SimpleMailMessage message = new SimpleMailMessage(); 
        message.setFrom("noreply@wp6.com");
        message.setTo(email); 
        message.setSubject("Envio de código para alteração de senha"); 
        message.setText(String.format("Olá! Segue o código para alterar sua senha:'%s'. Informe esse código no aplicativo para prosseguir.", key));
        mailSender.send(message);		
	}
	
	public String loginByEmailAndKey(String email, String key) {
		ForgotPasswordKeys entity = forgotPasswordKeysRepository.findTodayRecordByEmailANDKey(email, key);
		if (entity == null) {
			throw new CustomException("The email and forgot key don't exist", HttpStatus.NOT_FOUND);
		}
		
		Users user = usersRepository.findByEmail(email);
	    if (user == null) {
		      throw new CustomException("The user email doesn't exist", HttpStatus.NOT_FOUND);
	    }		
		
	    String username = user.getUsername();
		return jwtTokenProvider.createToken(username, usersRepository.findByUsername(username).getRoles());
	}
	
	public void passwordUpdateByEmail(String email, String newPassword) {
		Users user = usersRepository.findByEmail(email);
	    if (user == null) {
		      throw new CustomException("The user email doesn't exist", HttpStatus.NOT_FOUND);
	    }
	    user.setPassword(passwordEncoder.encode(newPassword));
	    usersRepository.save(user);
	}
	
	public List<ForgotPasswordQuestions> findAllForgotPasswordQuestions() {
		return forgotPasswordQuestionsRepository.findAll();
	}
	
	public void saveForgotPasswordQuestionsUsersAnswers(ForgotPasswordQuestionsUsersAnswers forgotPasswordQuestionsUsersAnswer) {
		if (!forgotPasswordQuestionsRepository.existsById(forgotPasswordQuestionsUsersAnswer.getForgotpasswordquestionsid())) {
			throw new CustomException("The Forgot Password Questions provided doesn't exist", HttpStatus.NOT_FOUND);
		}
		
		if (!usersRepository.existsById(forgotPasswordQuestionsUsersAnswer.getUsersid())) {
			throw new CustomException("The User provided doesn't exist", HttpStatus.NOT_FOUND);
		}
		
		forgotPasswordQuestionsUsersAnswersRepository.save(forgotPasswordQuestionsUsersAnswer);
	}
	
	public String loginByEmailAndAnswers(String email, List<ForgotPasswordQuestionsUsersAnswers> answers) {
		Users user = usersRepository.findByEmail(email);
	    if (user == null) {
	    	throw new CustomException("The user email doesn't exist", HttpStatus.NOT_FOUND);
	    }
	    
	    int correctAnswers = 0;
	    for (ForgotPasswordQuestionsUsersAnswers answer : answers) {
	    	ForgotPasswordQuestionsUsersAnswers answerComparison = forgotPasswordQuestionsUsersAnswersRepository.findByForgotPasswordQuestionsAndUserid(answer.getForgotpasswordquestionsid(), answer.getUsersid());
	    	if (answerComparison == null) {
	    		throw new CustomException("The comparison answer doesn't exist", HttpStatus.NOT_FOUND);
	    	}
	    	
	    	if (answer.getAnswer().equalsIgnoreCase(answerComparison.getAnswer())) {
	    		correctAnswers++;
	    	}
	    }
	    if (correctAnswers < 2) {
	    	throw new CustomException("Invalid answers supplied to login. Must have at least 2 correct ones.", HttpStatus.UNPROCESSABLE_ENTITY);
	    }
		
	    String username = user.getUsername();
		return jwtTokenProvider.createToken(username, usersRepository.findByUsername(username).getRoles());
	}
}