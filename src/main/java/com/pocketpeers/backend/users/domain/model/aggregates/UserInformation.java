package com.pocketpeers.backend.users.domain.model.aggregates;

import com.pocketpeers.backend.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.pocketpeers.backend.users.domain.model.commands.CreateUserInformationCommand;
import com.pocketpeers.backend.users.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
// Sin `name`: el nombre de la tabla lo sigue poniendo la estrategia de nombres
// del proyecto. Lo que se anade es la restriccion de unicidad, que es la unica
// garantia real contra dos cuentas con el mismo documento —la comprobacion en
// el servicio es solo para dar un mensaje legible, y dos registros a la vez se
// le escapan. Los nulos no chocan entre si en PostgreSQL, asi que las cuentas
// anteriores al cambio conviven sin problema.
@Table(uniqueConstraints = @UniqueConstraint(
        name = "ux_user_informations_identity_document",
        columnNames = {"document_type", "document_number"}))
public class UserInformation extends AuditableAbstractAggregateRoot<UserInformation> {

    @Getter
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;

    
    @Embedded
    private PersonName name;

    @Embedded
    private PhoneNumber phoneNumber;

    @Embedded
    private Photo photo;


    @Embedded
    EmailAddress email;

    /**
     * Documento de identidad, para acreditar la participacion en el estudio.
     *
     * <p>Admite nulo a proposito: las cuentas creadas antes de pedirlo no lo
     * tienen, y forzarlo aqui impediria que la aplicacion arrancara contra una
     * base existente. Quien si lo exige es el registro, que es donde importa
     * —desde ahora toda cuenta nueva lo trae—.</p>
     */
    @Embedded
    private IdentityDocument identityDocument;


    public UserInformation(String firstName, String lastName, String phoneNumber, String photo, String email, User user) {
        this(firstName, lastName, phoneNumber, photo, email, user, null);
    }

    public UserInformation(String firstName, String lastName, String phoneNumber, String photo, String email,
                           User user, IdentityDocument identityDocument) {
        this.name = new PersonName(firstName, lastName);
        this.phoneNumber = new PhoneNumber(phoneNumber);
        this.photo = new Photo(photo);
        this.email = new EmailAddress(email);
        this.user = user;
        this.identityDocument = identityDocument;
    }

    public UserInformation(CreateUserInformationCommand command) {
        this.name = new PersonName(command.firstName(), command.lastName());
        this.phoneNumber = new PhoneNumber(command.phoneNumber());
        this.photo = new Photo(command.photo());
        this.email = new EmailAddress(command.email());
        this.identityDocument = command.identityDocument();
        this.user = new User();
    }

    public UserInformation() {
    }



    public void updateName(String firstName, String lastName) {
        this.name = new PersonName(firstName, lastName);
    }

    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = new PhoneNumber(phoneNumber);
    }

    public void updatePhoto(String photo) {
        this.photo = new Photo(photo);
    }

    public void updateEmail(String email) {
        this.email = new EmailAddress(email);
    }

    
    public String getFullName() {
        return name.getFullName();
    }

    public String getEmailAddress() {
        return email.email();
    }

    public String getPhoneNumber() {
        return phoneNumber.getPhoneNumber();
    }

    public String getPhoto() {
        return photo.getPhoto();
    }


    
    public void setName(PersonName name) {
        this.name = name;
    }

    public void setEmail(EmailAddress email) {
        this.email = email;
    }
}
