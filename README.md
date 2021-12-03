# Functional Scala 2021

Projects contains 2 subprojects:
* external-secret-opeartor
* login-app


## External Secret Operator
Kubernetes operator that syncs secrets from AWS Secret manager into K8s Secrets.

You can build it using:
```bash
sbt operator/docker:publishLocal
```

It will build and publish docker image of operator in your local docker registry.

To deploy it in local cluster (docker-desktop, minikube) you can use command

```bash
kubectl apply -f k8/operator
```

It will create these kubernetes objects:
* `operator` namespace
* `aws-secert` Secret that will be used to communicate with AWS API
* `externa-secret-operator` ServiceAccount
* `external-secret-operator-role` Role
* `external-secret-operator-role-binding` RoleBinding
* `externa-secret-operator` Deployment
* `externa-secret-operator` Service

In `aws-secert` Secret you should add your AWS credentials.

## Login app
ZIO Http app that exposes endpoint to test authentication using secret data.

You can build it using:
```bash
sbt loginApp/docker:publishLocal
```

It will build and publish docker image of login app in your local docker registry.

To deploy it in local cluster (docker-desktop, minikube) you can use command

```bash
kubectl apply -f k8/app
```

It will create these kubernetes objects:
* `login-app` Service account
* `login-app` Deployment
* `login-app` Service

Befor testing it you should port forward port so that it is accessible outside of K8s cluster using

```bash
kubectl port-forward service/login-app  9000:9000
```

Now you can test it using
```bash
curl -i  localhost:9000/login/jkobejs/fscala2021
```

## External secret

Before creating external secret you should create on your AWS account `fpscala2021` secret with username and password. 

Now when you create external sercret using

```bash
kubectl apply -f k8/externalsecrets
```

External secret operator will sync it into your cluster and you can call login app to see if it works.
